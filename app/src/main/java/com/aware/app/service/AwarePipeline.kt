package com.aware.app.service

import com.aware.app.alert.AlertEngine
import com.aware.app.alert.Category
import com.aware.app.alert.CategoryMapper
import com.aware.app.alert.Detection
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import android.util.Log
import com.aware.app.audio.AudioSource
import com.aware.app.audio.rms
import com.aware.app.audio.toDbfs
import com.aware.app.classify.Label
import com.aware.app.classify.YamnetClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedItem(
    val timeMs: Long,
    val type: SoundType,
    val score: Float,
    val trend: Trend,
    val text: String? = null,
    val nameCalled: Boolean = false,
)

data class PipelineState(
    val listening: Boolean = false,
    val labels: List<Label> = emptyList(),
    val levelDb: Float = -90f,
    val error: String? = null,
    val latestAlert: Detection? = null,
    val feed: List<FeedItem> = emptyList(),
    val alertsPaused: Boolean = false,
    val latestNameAlertMs: Long? = null,
)

class AwarePipeline(
    private val audioSource: AudioSource,
    private val classifier: YamnetClassifier,
) {
    private val mutableState = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = mutableState.asStateFlow()
    private val mutableAlerts = MutableSharedFlow<Detection>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alerts: SharedFlow<Detection> = mutableAlerts.asSharedFlow()
    private val mutableSpeechActive = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val speechActive: SharedFlow<Unit> = mutableSpeechActive.asSharedFlow()
    private var job: Job? = null
    private val alertEngine = AlertEngine()
    private var lastSpeechMs: Long? = null

    @Synchronized
    fun setSensitivity(sensitivity: Float) {
        alertEngine.sensitivity = sensitivity
    }

    @Synchronized
    fun setEnabledCategories(categories: Set<Category>) {
        alertEngine.enabledCategories = categories
    }

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val previous = job
        alertEngine.reset()
        lastSpeechMs = null
        mutableState.value = mutableState.value.copy(listening = true, alertsPaused = false, error = null)
        val next = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val session = currentCoroutineContext()[Job]
            try {
                // A cancelled source must finish releasing its recorder before a restart.
                previous?.join()
                var failures = 0
                var silentWindows = 0
                val handle: suspend (FloatArray) -> Unit = handle@{ samples ->
                    failures = 0
                    val level = toDbfs(rms(samples))
                    // Android hands back exact silence when the microphone is blocked or taken by another app.
                    silentWindows = if (level <= SILENT_DB) silentWindows + 1 else 0
                    val labels = classifier.classify(samples, audioSource.sampleRate)
                    currentCoroutineContext().ensureActive()
                    synchronized(this@AwarePipeline) {
                        if (job === session && session?.isActive == true) {
                            val nowMs = System.currentTimeMillis()
                            val detections = alertEngine.process(labels, level, nowMs)
                            Log.d(TAG, "%.0f dB | %s | fired=%s".format(
                                level,
                                labels.take(8).joinToString { "${it.name} ${(it.score * 100).toInt()}%" },
                                detections.filter { it.isNew }.map { it.type },
                            ))
                            val previousState = mutableState.value
                            val newDetections = detections.filter { it.isNew }
                            newDetections.forEach { mutableAlerts.tryEmit(it) }
                            // Updates refresh the trend but keep the settled confidence.
                            val current = previousState.latestAlert?.let { alert ->
                                detections.firstOrNull { !it.isNew && it.type == alert.type }
                                    ?.let { alert.copy(trend = it.trend, levelDb = it.levelDb) }
                                    ?: alert
                            }
                            val strongestNew = newDetections.maxByOrNull { it.score }
                            // A weaker alert doesn't replace a stronger recent one unless it's an emergency.
                            val latestAlert = when {
                                strongestNew == null -> current
                                current == null ||
                                    strongestNew.type.category == Category.EMERGENCY ||
                                    strongestNew.score >= current.score ||
                                    nowMs - current.timeMs >= ALERT_HOLD_MS -> strongestNew
                                else -> current
                            }
                            val additions = newDetections.sortedByDescending { it.score }.map {
                                FeedItem(it.timeMs, it.type, it.score, it.trend)
                            }.toMutableList()
                            val speechScore = labels.filter {
                                CategoryMapper.map(it.name) == SoundType.SPEECH
                            }.maxOfOrNull { it.score } ?: 0f
                            val previousSpeechMs = lastSpeechMs
                            if (speechScore.isFinite() && speechScore >= alertEngine.thresholdFor(SoundType.SPEECH)) {
                                mutableSpeechActive.tryEmit(Unit)
                            }
                            if (Category.PEOPLE in alertEngine.enabledCategories &&
                                speechScore.isFinite() &&
                                speechScore >= alertEngine.thresholdFor(SoundType.SPEECH) &&
                                (previousSpeechMs == null || nowMs - previousSpeechMs >= SPEECH_FEED_INTERVAL_MS)
                            ) {
                                additions += FeedItem(nowMs, SoundType.SPEECH, speechScore, alertEngine.trend)
                                lastSpeechMs = nowMs
                            }
                            mutableState.value = PipelineState(
                                listening = true,
                                labels = labels,
                                levelDb = level,
                                latestAlert = latestAlert,
                                feed = (additions + previousState.feed).take(FEED_LIMIT),
                                latestNameAlertMs = previousState.latestNameAlertMs,
                                error = if (silentWindows >= SILENT_WINDOWS) MIC_SILENT else null,
                            )
                        }
                    }
                }
                // A capture failure (another app took the microphone, a headset switch) retries with backoff
                // instead of leaving the app deaf until the user notices and restarts it.
                while (true) {
                    try {
                        audioSource.windows().collect(handle)
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "Audio capture failed; retrying", error)
                        synchronized(this@AwarePipeline) {
                            if (job === session && session?.isActive == true) {
                                mutableState.update {
                                    it.copy(listening = false, labels = emptyList(), levelDb = -90f,
                                        error = error.message ?: "Audio capture or classification failed.")
                                }
                            }
                        }
                        delay((1_000L shl failures.coerceAtMost(4)).coerceAtMost(15_000L))
                        failures++
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(this@AwarePipeline) {
                    if (job === session && session?.isActive == true) {
                        mutableState.update {
                            it.copy(
                                listening = false,
                                labels = emptyList(),
                                levelDb = -90f,
                                error = error.message ?: "Audio capture or classification failed.",
                            )
                        }
                    }
                }
            } finally {
                synchronized(this@AwarePipeline) {
                    if (job === session) {
                        mutableState.update { it.copy(listening = false, levelDb = -90f) }
                    }
                }
            }
        }
        job = next
        if (!next.start()) mutableState.value = PipelineState()
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        alertEngine.reset()
        lastSpeechMs = null
        mutableState.value = PipelineState()
    }

    /** Await AudioRecord's finally block before a legacy recognizer opens its microphone. */
    suspend fun pauseForCaptions() {
        val previous = synchronized(this) {
            mutableState.update { it.copy(alertsPaused = true, labels = emptyList(), levelDb = -90f) }
            job.also { it?.cancel() }
        }
        previous?.cancelAndJoin()
    }

    @Synchronized
    fun recordName(timeMs: Long) {
        mutableState.update { it.copy(
            latestNameAlertMs = timeMs,
            feed = (listOf(FeedItem(timeMs, SoundType.SPEECH, 1f, Trend.STEADY, nameCalled = true)) + it.feed).take(FEED_LIMIT),
        ) }
    }

    @Synchronized
    fun recordTranscript(text: String, timeMs: Long) {
        mutableState.update { state ->
            val index = state.feed.indexOfFirst { it.type == SoundType.SPEECH && !it.nameCalled }
            val feed = state.feed.toMutableList()
            if (index >= 0) feed[index] = feed[index].copy(text = text)
            else feed.add(0, FeedItem(timeMs, SoundType.SPEECH, 1f, Trend.STEADY, text = text))
            state.copy(feed = feed.take(FEED_LIMIT))
        }
    }

    private companion object {
        const val FEED_LIMIT = 30
        const val SPEECH_FEED_INTERVAL_MS = 3_000L
        const val ALERT_HOLD_MS = 3_000L
        const val TAG = "Aware"
        const val SILENT_DB = -89f
        const val SILENT_WINDOWS = 20 // about ten seconds of exact silence
        const val MIC_SILENT = "The microphone is returning silence. Another app may be using it, or access is blocked."
    }
}
