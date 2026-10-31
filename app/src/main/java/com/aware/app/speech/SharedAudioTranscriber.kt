package com.aware.app.speech

import android.content.Context
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/** A null PCM source is used only by MicTranscriber, which owns the legacy microphone. */
class SharedAudioTranscriber internal constructor(
    private val context: Context,
    private val pcm: SharedFlow<ShortArray>?,
    private val locale: Locale = Locale.getDefault(),
    private val onError: (Int?) -> Unit = {},
) : Transcriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableResults = MutableSharedFlow<TranscriptEvent>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: Flow<TranscriptEvent> = mutableResults.asSharedFlow()
    private var recognizer: SpeechRecognizer? = null
    private var pipe: Array<ParcelFileDescriptor>? = null
    private var writer: Job? = null
    private var restart: Job? = null
    private var active = false
    private var released = false
    private var generation = 0
    private var errors = 0

    override fun start() {
        checkMain()
        if (active || released) return
        active = true
        errors = 0
        beginSession()
    }

    private fun beginSession() {
        if (!active) return
        val token = ++generation
        try {
            val device = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = device
            device.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = publish(partialResults, false, token)
                override fun onSegmentResults(segmentResults: Bundle) {
                    if (!active || token != generation) return
                    publish(segmentResults, true, token)
                    errors = 0
                    this@SharedAudioTranscriber.onError(null)
                }
                override fun onEndOfSegmentedSession() {
                    if (active && token == generation) scheduleRestart(250)
                }
                override fun onResults(results: Bundle?) {
                    if (!active || token != generation) return
                    publish(results, true, token)
                    errors = 0
                    this@SharedAudioTranscriber.onError(null)
                    scheduleRestart(250)
                }
                override fun onError(error: Int) {
                    // Includes ERROR_CLIENT caused by cancel/destroy after stop.
                    if (!active || token != generation) return
                    this@SharedAudioTranscriber.onError(error)
                    scheduleRestart(backoff())
                }
            })
            val intent = recognitionIntent(locale)
            if (pcm != null && Build.VERSION.SDK_INT >= 33) {
                val descriptors = ParcelFileDescriptor.createPipe()
                pipe = descriptors
                val fd = descriptors[1].fileDescriptor
                Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptors[0])
                // An injected stream stays open continuously. Ask for finalized
                // segments instead of waiting for EOF before onResults fires.
                intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                writer = scope.launch(Dispatchers.IO) {
                    try {
                        pcm.collect { samples ->
                            val bytes = ByteArray(samples.size * 2)
                            samples.forEachIndexed { index, sample ->
                                bytes[index * 2] = sample.toByte()
                                bytes[index * 2 + 1] = (sample.toInt() shr 8).toByte()
                            }
                            var offset = 0
                            while (offset < bytes.size) {
                                ensureActive()
                                try {
                                    offset += Os.write(fd, bytes, offset, bytes.size - offset)
                                } catch (error: ErrnoException) {
                                    if (error.errno != OsConstants.EAGAIN && error.errno != OsConstants.EINTR) throw error
                                    delay(10)
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            if (active && token == generation) {
                                onError(SpeechRecognizer.ERROR_AUDIO)
                                scheduleRestart(backoff())
                            }
                        }
                    }
                }
            }
            device.startListening(intent)
        } catch (_: Exception) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            scheduleRestart(backoff())
        }
    }

    private fun publish(bundle: Bundle?, final: Boolean, token: Int) {
        if (!active || token != generation) return
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) mutableResults.tryEmit(TranscriptEvent(text, final, System.currentTimeMillis()))
    }

    private fun backoff(): Long = (500L * (1L shl (++errors).coerceAtMost(5))).coerceAtMost(15_000)

    private fun scheduleRestart(delayMs: Long) {
        // Invalidate callbacks immediately, but destroy outside the recognizer callback stack.
        generation++
        restart?.cancel()
        restart = scope.launch {
            yield()
            closeSession()
            delay(delayMs)
            if (active) beginSession()
        }
    }

    private fun closeSession() {
        writer?.cancel()
        writer = null
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        pipe?.forEach { runCatching { it.close() } }
        pipe = null
    }

    override fun stop() {
        checkMain()
        active = false
        generation++
        restart?.cancel()
        restart = null
        closeSession()
    }

    override fun release() {
        stop()
        released = true
        scope.cancel()
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
