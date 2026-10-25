package com.aware.app.speech

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.aware.app.audio.MicAudioSource
import com.aware.app.data.Settings
import com.aware.app.service.AwarePipeline
import com.aware.app.service.ListeningRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Owned by the service; all control and recognizer callbacks run on Main. */
class SpeechController(
    context: Context,
    private val pipeline: AwarePipeline,
    audio: MicAudioSource,
    private val scope: CoroutineScope,
    private val onName: (Long) -> Unit,
) {
    private val transcriber: Transcriber = if (Build.VERSION.SDK_INT >= 33) {
        SharedAudioTranscriber(context, audio.pcm, onError = ::recognizerError)
    } else MicTranscriber(context, onError = ::recognizerError)
    private val translator = LiveTranslator(context)
    private val detector = NameDetector(SystemClock::elapsedRealtime)
    private var settings = Settings()
    private var continuous = false
    private var active = false
    private var closed = false
    private var burstStart = 0L
    private var burstUntil = 0L
    private var nextBurst = 0L
    private var timer: Job? = null
    private var transition: Job? = null
    private val jobs = mutableListOf<Job>()

    private fun recognizerError(code: Int?) {
        ListeningRepository.speechError(code)
        if (code != null) ListeningRepository.clearPartial()
    }

    init {
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transcriber.results.collect { event ->
                if (!active) return@collect
                val line = ListeningRepository.appendTranscript(event)
                if (event.isFinal) pipeline.recordTranscript(event.text, event.timeMs)
                if (settings.nameDetection && detector.matches(event.text)) onName(event.timeMs)
                translator.translate(event) { ListeningRepository.translateLine(line, it) }
            }
        }
        jobs += scope.launch { pipeline.speechActive.collect { speechWindow() } }
        jobs += scope.launch { ListeningRepository.captions.collect { setContinuous(it) } }
    }

    fun applySettings(value: Settings) {
        settings = value
        detector.userName = value.userName
        ListeningRepository.setTranslationTarget(value.translationTarget)
        if (active) translator.setTarget(value.translationTarget)
        if (!continuous && (!value.nameDetection || value.userName.isBlank())) end()
    }

    private fun speechWindow() {
        if (closed || Build.VERSION.SDK_INT < 33 || continuous || !settings.nameDetection || settings.userName.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (!active) {
            if (now < nextBurst) return
            burstStart = now
            begin()
        }
        burstUntil = minOf(now + 6_000, burstStart + 15_000)
        timer?.cancel()
        timer = scope.launch {
            delay((burstUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            end()
            nextBurst = SystemClock.elapsedRealtime() + 1_000
        }
    }

    private fun setContinuous(enabled: Boolean) {
        if (closed || continuous == enabled) return
        continuous = enabled
        timer?.cancel()
        if (Build.VERSION.SDK_INT >= 33) {
            if (enabled) begin() else end()
        } else {
            // Serialize pause/resume, including quick toggles while AudioRecord is releasing.
            transition?.cancel()
            transition = scope.launch {
                if (enabled) {
                    pipeline.pauseForCaptions()
                    ensureActive()
                    begin()
                } else {
                    end()
                    pipeline.start(scope)
                }
            }
        }
    }

    private fun begin() {
        if (active || closed) return
        active = true
        translator.setTarget(settings.translationTarget)
        transcriber.start()
    }

    private fun end() {
        timer?.cancel()
        transcriber.stop()
        translator.close()
        active = false
        ListeningRepository.clearPartial()
    }

    fun release() {
        closed = true
        jobs.forEach { it.cancel() }
        transition?.cancel()
        end()
        transcriber.release()
    }
}
