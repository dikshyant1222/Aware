package com.aware.app.llm

import android.content.Context
import android.util.Log
import com.aware.app.data.SummaryHistoryRepository
import com.aware.app.data.SummaryRecord
import java.io.IOException
import com.aware.app.alert.AlertMessages
import com.aware.app.alert.Category
import com.aware.app.alert.Detection
import com.aware.app.service.AwarePipeline
import com.aware.app.service.ListeningRepository
import com.aware.app.speech.TranscriptLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Service-owned; buffer/trigger/publication work is confined to Main. Every new alert is assessed twice:
 * instantly by [SituationRules], then by the model, which can refine the wording and the urgency.
 * [onEscalate] is called only when the situation is more serious than the alerts already delivered.
 */
class SituationController(
    context: Context,
    pipeline: AwarePipeline,
    private val scope: CoroutineScope,
    private val onEscalate: (Urgency, String, Boolean) -> Unit,
) {
    private val messages = AlertMessages(context::getString)
    private val template = TemplateSummarizer({ it.displayName }) {
        messages.templateFor(Detection(it.type, it.confidence, it.trend, -90f, 0, true))
    }
    private val llm = LiteRtLlmSummarizer(context)
    private val events = mutableListOf<Detection>()
    private val lines = linkedMapOf<Long, TranscriptLine>()
    private val jobs = mutableListOf<Job>()
    private val history = SummaryHistoryRepository(context)
    private var generation = 0L
    private var summaryJob: Job? = null
    private var alertJob: Job? = null
    private var escalatedLevel = Urgency.LOW
    private var escalatedAt = 0L
    private var refined = false
    private var lastSignature: Any? = null

    init {
        llm.warmUp()
        val now = System.currentTimeMillis()
        ListeningRepository.transcript.value.filter { it.isFinal && it.text.isNotBlank() && now - it.timeMs in 0..40_000L }
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
                // A muted repeat keeps its mute; anything else is fresh evidence and lifts them all.
                if (!AlertTriage.isMuted(event.type, time)) AlertTriage.clear()
                // The rules run now; the model gets a short debounce and waits for any run in progress.
                val snapshot = snapshot(time)
                escalate(SituationRules.floor(snapshot), snapshot, template.summarize(snapshot).text, false, time)
                alertJob?.cancel()
                alertJob = scope.launch {
                    delay(ALERT_DEBOUNCE_MS)
                    summaryJob?.join()
                    trigger(System.currentTimeMillis())
                }
            }
        }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ListeningRepository.transcript.collect { transcript ->
                val time = System.currentTimeMillis()
                val finals = transcript.filter { it.isFinal && it.text.isNotBlank() && time - it.timeMs in 0..40_000L }
                finals.forEach { lines[it.id] = it }
                prune(time)

            }
        }
        jobs += scope.launch { runSummaryTicker { trigger(System.currentTimeMillis()) } }
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ListeningRepository.situationRequests.collect { trigger(System.currentTimeMillis(), force = true) }
        }
        jobs += scope.launch {
            ListeningRepository.modelChanges.collect {
                generation++
                lastSignature = null
                summaryJob?.cancel()
                llm.refresh()
            }
        }
    }

    private fun prune(now: Long) {
        events.removeAll { now - it.timeMs !in 0..40_000L }
        lines.entries.removeAll { now - it.value.timeMs !in 0..40_000L }
    }

    private fun snapshot(now: Long): SituationContext {
        prune(now)
        return SituationContext(
            events.map { SituationContext.Event(it.type, it.type.category, it.trend, it.score, (now - it.timeMs) / 1000.0) },
            lines.values.map { SituationContext.SpeechLine(it.text, (now - it.timeMs) / 1000.0, it.translation) },
            maxAgeSeconds = 40.0,
        )
    }

    /** Re-summarize only when the evidence changed, so a quiet room doesn't run the model every 40 seconds. */
    private fun trigger(now: Long, force: Boolean = false) {
        val context = snapshot(now)
        val signature = events.map { it.type to it.timeMs } to lines.values.map { it.id to it.text }
        if (!force && signature == lastSignature) return
        lastSignature = signature
        val floor = SituationRules.floor(context)
        val hasEmergency = context.events.any { it.category == Category.EMERGENCY }
        val token = ++generation
        summaryJob?.cancel()
        summaryJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val quick = template.summarize(context)
            publish(quick.copy(urgency = floor), now, now)
            if (context.events.isEmpty() && context.speech.isEmpty()) return@launch
            llm.summarize(context)?.let {
                if (generation != token) return@let
                val urgency = SituationRules.resolve(floor, it.urgency, hasEmergency)
                publish(it.copy(urgency = urgency), System.currentTimeMillis(), now)
                if (urgency == Urgency.LOW && context.speech.isEmpty()) {
                    AlertTriage.muteIfBackground(context.events.map { event -> event.type }, now)
                }
                escalate(urgency, context, it.text, true, System.currentTimeMillis())
            }
        }
    }

    /** Escalate only past what the individual alerts already delivered, and don't repeat within 30 seconds. */
    private fun escalate(level: Urgency, context: SituationContext, text: String, fromModel: Boolean, now: Long) {
        val delivered = if (context.events.any { it.category == Category.EMERGENCY }) Urgency.HIGH else Urgency.NORMAL
        if (level < Urgency.HIGH || level <= delivered) return
        val recent = now - escalatedAt in 0..30_000L
        if (recent && level <= escalatedLevel) {
            // The model's wording replaces the template text on the notification already shown.
            if (fromModel && level == escalatedLevel && !refined) {
                refined = true
                onEscalate(level, text, true)
            }
            return
        }
        escalatedLevel = level
        escalatedAt = now
        refined = fromModel
        onEscalate(level, text, false)
    }

    private suspend fun publish(summary: Summary, timeMs: Long, cycleId: Long) {
        ListeningRepository.publishSituation(summary, timeMs)
        try { history.publish(SummaryRecord(cycleId, timeMs, summary)) }
        catch (error: IOException) { Log.w("AwareSummary", "Could not save recent summary", error) }
    }

    fun close() {
        generation++
        summaryJob?.cancel()
        alertJob?.cancel()
        jobs.forEach { it.cancel() }
        llm.close()
    }

    private companion object {
        const val ALERT_DEBOUNCE_MS = 1_500L
    }
}
