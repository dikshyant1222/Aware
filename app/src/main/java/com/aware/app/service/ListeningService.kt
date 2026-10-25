package com.aware.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aware.app.MainActivity
import com.aware.app.R
import com.aware.app.alert.AlertReactor
import com.aware.app.alert.Detection
import com.aware.app.audio.MicAudioSource
import com.aware.app.classify.YamnetClassifier
import com.aware.app.data.settingsRepository
import com.aware.app.speech.SpeechController
import com.aware.app.llm.SituationController
import kotlinx.coroutines.flow.first
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListeningService : LifecycleService() {
    private var classifier: YamnetClassifier? = null
    private var pipeline: AwarePipeline? = null
    private var reactor: AlertReactor? = null
    private var speech: SpeechController? = null
    private var situation: SituationController? = null
    private var sessionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> if (!running) startListening()
            ACTION_STOP -> stopListening()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun startListening() {
        try {
            notifications.createNotificationChannel(NotificationChannel(
                CHANNEL_LISTENING, getString(R.string.listening_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            })
            AlertReactor.createChannel(this)
            // Promote before model loading or microphone access, including on API 34+.
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
            running = true
            ListeningRepository.update(PipelineState(listening = true), true)
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aware:listening").apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1_000L)
                }
            sessionJob = lifecycleScope.launch {
                // Keep the temporary model even if cancellation wins the dispatch back to Main.
                var loadedModel: YamnetClassifier? = null
                try {
                    withContext(Dispatchers.Default) { loadedModel = YamnetClassifier(applicationContext) }
                    ensureActive()
                    val model = checkNotNull(loadedModel)
                    classifier = model
                    loadedModel = null
                    val audio = MicAudioSource()
                    val activePipeline = AwarePipeline(audio, model)
                    pipeline = activePipeline
                    val activeReactor = AlertReactor(this@ListeningService)
                    reactor = activeReactor
                    // Keep child failures inside this try/catch, including notification/haptic failures.
                    coroutineScope {
                        val initialSettings = settingsRepository.settings.first()
                        activePipeline.setSensitivity(initialSettings.sensitivity)
                        activePipeline.setEnabledCategories(initialSettings.enabledCategories)
                        // Start capture before a legacy captions request can pause it.
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            activePipeline.alerts.collect(activeReactor::react)
                        }
                        val situationController = SituationController(this@ListeningService, activePipeline, this, activeReactor::escalate)
                        situation = situationController
                        activePipeline.start(this)
                        val controller = SpeechController(this@ListeningService, activePipeline, audio, this) { time ->
                            activePipeline.recordName(time)
                            activeReactor.nameCalled(time)
                        }
                        speech = controller
                        controller.applySettings(initialSettings)
                        launch {
                            settingsRepository.settings.collect { settings ->
                                activePipeline.setSensitivity(settings.sensitivity)
                                activePipeline.setEnabledCategories(settings.enabledCategories)
                                controller.applySettings(settings)                            }
                        }
                        launch {
                            activePipeline.state.map { it.latestAlert }.filterNotNull()
                                .distinctUntilChanged { old, new -> old.type == new.type && old.timeMs == new.timeMs }
                                .sample(3_000).collect { alert ->
                                    // Foreground promotion still runs when notification permission is denied.
                                    if (notifications.areNotificationsEnabled()) {
                                        try {
                                            notifications.notify(NOTIFICATION_ID, notification(alert))
                                        } catch (_: SecurityException) {
                                            // Notification permission may have changed since the check.
                                        }
                                    }
                                }
                        }
                        activePipeline.state.collect { state ->
                            ListeningRepository.update(state, true)
                            if (!state.listening && !state.alertsPaused) stopListening(state.error)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    stopListening(error.message ?: getString(R.string.listening_start_error))
                } finally {
                    // A stop during native model loading must still close the eventual model.
                    runCatching { loadedModel?.close() }
                        .onFailure { Log.w(TAG, "Could not close the loading classifier", it) }
                }
            }
        } catch (error: Exception) {
            // Includes SecurityException for revoked mic permission or an ineligible FGS start.
            stopListening(error.message ?: getString(R.string.listening_start_error))
        }
    }

    private fun notification(alert: Detection? = null): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PENDING_FLAGS)
        val stop = PendingIntent.getService(this, 0, Intent(this, ListeningService::class.java).apply {
            action = ACTION_STOP
        }, PENDING_FLAGS)
        val text = alert?.let {
            getString(R.string.listening_latest_alert, it.type.displayName,
                DateFormat.getTimeFormat(this).format(Date(it.timeMs)))
        } ?: getString(R.string.listening_background_hint)
        return NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.listening_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.listening_stop), stop)
            .build()
    }

    private fun stopListening(error: String? = null) {
        running = false
        sessionJob?.cancel()
        sessionJob = null
        // A failing native cleanup must not leave the other resources or the FGS alive.
        var failure = error
        fun cleanUp(block: () -> Unit) {
            try {
                block()
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Listening cleanup failed", cleanupError)
                if (failure == null) failure = cleanupError.message ?: getString(R.string.listening_start_error)
            }
        }
        cleanUp { situation?.close() }
        situation = null
        cleanUp { speech?.release() }
        speech = null
        ListeningRepository.stopped()
        cleanUp { pipeline?.stop() }
        pipeline = null
        cleanUp { reactor?.stop() }
        reactor = null
        cleanUp { classifier?.close() }
        classifier = null
        cleanUp { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
        cleanUp { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        ListeningRepository.update(PipelineState(error = failure), false)
        stopSelf()
    }

    override fun onDestroy() {
        // Keep failures visible until retry; normal stops reset to defaults.
        stopListening(ListeningRepository.state.value.error)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Aware"
        const val ACTION_START = "com.aware.app.action.START_LISTENING"
        const val ACTION_STOP = "com.aware.app.action.STOP_LISTENING"
        private const val CHANNEL_LISTENING = "listening"
        private const val NOTIFICATION_ID = 1
        private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
