package com.aware.app.llm

import android.content.Context
import com.aware.app.alert.AlertMessages
import com.aware.app.alert.Detection
import com.aware.app.service.AwarePipeline
import com.aware.app.service.ListeningRepository
import com.aware.app.speech.TranscriptLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Service-owned; buffer/trigger/publication work is confined to Main. */
class SituationController(context: Context, pipeline: AwarePipeline, private val scope: CoroutineScope, enabled: Boolean) {
    private val messages = AlertMessages(context::getString)
    private val template = TemplateSummarizer({ it.displayName }) {
        messages.templateFor(Detection(it.type, it.confidence, it.trend, -90f, 0, true))
    }
    private val llm = MediaPipeLlmSummarizer(context)
    private var smart = enabled
    private val events = mutableListOf<Detection>()
    private val lines = linkedMapOf<Long, TranscriptLine>()
    private val jobs = mutableListOf<Job>()
    private var lastAutoMs: Long? = null
    private var generation = 0L
    private var summaryJob: Job? = null

    init {
        llm.setEnabled(enabled)
        val now = System.currentTimeMillis()
        ListeningRepository.transcript.value.filter { it.isFinal && it.text.isNotBlank() && now - it.timeMs in 0..30_000L }
            .forEach { lines[it.id] = it }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            llm.status.collect { ListeningRepository.updateLlmStatus(it) }
        }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            pipeline.alerts.collect { event ->
                if (!event.isNew || !event.type.alerts) return@collect
                events += event
                val time = System.currentTimeMillis()
                prune(time)
                if (lines.values.any { shouldAutoSummarize(event.type, event.timeMs, it.timeMs, time, lastAutoMs) }) {
                    lastAutoMs = time
                    trigger(time)
                }
            }
        }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ListeningRepository.transcript.collect { transcript ->
                val time = System.currentTimeMillis()
                val finals = transcript.filter { it.isFinal && it.text.isNotBlank() && time - it.timeMs in 0..30_000L }
                val newLines = finals.filter { it.id !in lines }
                finals.forEach { lines[it.id] = it }
                prune(time)
                if (newLines.any { line -> events.any { shouldAutoSummarize(it.type, it.timeMs, line.timeMs, time, lastAutoMs) } }) {
                    lastAutoMs = time
                    trigger(time)
                }
            }
        }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ListeningRepository.situationRequests.collect { trigger(System.currentTimeMillis()) }
        }
        jobs += scope.launch {
            ListeningRepository.modelChanges.collect {
                generation++
                summaryJob?.cancel()
                llm.refresh()
            }
        }
    }

    private fun prune(now: Long) {
        events.removeAll { now - it.timeMs !in 0..30_000L }
        lines.entries.removeAll { now - it.value.timeMs !in 0..30_000L }
    }

    private fun trigger(now: Long) {
        prune(now)
        val context = SituationContext(
            events.map { SituationContext.Event(it.type, it.type.category, it.trend, it.score, (now - it.timeMs) / 1000.0) },
            lines.values.map { SituationContext.SpeechLine(it.text, (now - it.timeMs) / 1000.0, it.translation) },
        )
        val token = ++generation
        summaryJob?.cancel()
        summaryJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ListeningRepository.publishSituation(template.summarize(context), now)
            if (smart) llm.summarize(context)?.let {
                if (smart && generation == token) ListeningRepository.publishSituation(it, System.currentTimeMillis())
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (smart == enabled) return
        smart = enabled
        generation++
        summaryJob?.cancel()
        llm.setEnabled(enabled)
    }

    fun close() {
        generation++
        summaryJob?.cancel()
        jobs.forEach { it.cancel() }
        llm.close()
    }
}
