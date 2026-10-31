package com.aware.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aware.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.aware.app.speech.TranscriptEvent
import com.aware.app.speech.TranscriptLine
import com.aware.app.llm.Summary
import com.aware.app.llm.LlmStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object ListeningRepository {
    private val mutableSituation = MutableStateFlow<Summary?>(null)
    val situation: StateFlow<Summary?> = mutableSituation.asStateFlow()
    private val mutableSituationTime = MutableStateFlow(0L)
    val situationTimeMs: StateFlow<Long> = mutableSituationTime.asStateFlow()
    private val mutableLlmStatus = MutableStateFlow<LlmStatus>(LlmStatus.NOT_INSTALLED)
    val llmStatus: StateFlow<LlmStatus> = mutableLlmStatus.asStateFlow()
    // Retain a tap during classifier startup, before the service's controller subscribes.
    private val requests = Channel<Unit>(Channel.CONFLATED)
    internal val situationRequests = requests.receiveAsFlow()
    private val modelUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val modelChanges = modelUpdates.asSharedFlow()

    fun requestSituation() { if (running.value) requests.trySend(Unit) }
    fun modelChanged() { modelUpdates.tryEmit(Unit) }
    internal fun updateLlmStatus(status: LlmStatus) { mutableLlmStatus.value = status }
    internal fun publishSituation(summary: Summary, timeMs: Long) {
        mutableSituationTime.value = timeMs
        mutableSituation.value = summary
    }
    private val mutableState = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = mutableState.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()
    private val mutableCaptions = MutableStateFlow(false)
    val captions: StateFlow<Boolean> = mutableCaptions.asStateFlow()
    private val mutableTranscript = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val transcript: StateFlow<List<TranscriptLine>> = mutableTranscript.asStateFlow()
    private val mutableSpeechError = MutableStateFlow<Int?>(null)
    val speechError: StateFlow<Int?> = mutableSpeechError.asStateFlow()
    private var nextLineId = 0L
    private var translationTarget: String? = null

    fun setCaptions(context: Context, enabled: Boolean) {
        mutableCaptions.value = enabled
        if (enabled) start(context)
    }

    fun clearTranscript() { mutableTranscript.value = emptyList() }

    internal fun appendTranscript(event: TranscriptEvent): TranscriptLine {
        val old = mutableTranscript.value
        val partial = old.lastOrNull { !it.isFinal }
        val line = TranscriptLine(
            partial?.id ?: ++nextLineId, event.text, event.isFinal, event.timeMs,
            // Keep the last completed partial translation until the next throttled request finishes.
            translation = if (event.isFinal) null else partial?.translation,
        )
        val finals = old.filter { it.isFinal }
        mutableTranscript.value = if (event.isFinal) (finals + line).takeLast(50) else finals.takeLast(50) + line
        return line
    }

    internal fun translateLine(line: TranscriptLine, text: String) {
        mutableTranscript.value = mutableTranscript.value.map {
            if (it.id == line.id && it.isFinal == line.isFinal) it.copy(translation = text) else it
        }
    }
    internal fun setTranslationTarget(code: String?) {
        if (translationTarget != code) {
            translationTarget = code
            mutableTranscript.value = mutableTranscript.value.map { it.copy(translation = null) }
        }
    }
    internal fun clearPartial() {
        mutableTranscript.value = mutableTranscript.value.filter { it.isFinal }
    }
    internal fun speechError(code: Int?) { mutableSpeechError.value = code }
    internal fun stopped() {
        requests.tryReceive()
        mutableSituation.value = null
        mutableSituationTime.value = 0L
        mutableCaptions.value = false
        mutableSpeechError.value = null
        clearPartial()
    }

    fun start(context: Context) {
        if (running.value) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java).apply {
                action = ListeningService.ACTION_START
            })
        } catch (error: RuntimeException) {
            stopped()
            update(PipelineState(error = error.message ?: context.getString(R.string.listening_start_error)), false)
        }
    }

    fun stop(context: Context) {
        // onDestroy performs the same cleanup as ACTION_STOP, without creating an idle service.
        context.stopService(Intent(context, ListeningService::class.java))
    }

    internal fun update(state: PipelineState, running: Boolean) {
        mutableState.value = state
        mutableRunning.value = running
    }
}
