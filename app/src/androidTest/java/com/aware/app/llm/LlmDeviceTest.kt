package com.aware.app.llm

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Uses the installed native model, not a mock or template. No microphone or UI required. */
@RunWith(AndroidJUnit4::class)
class LlmDeviceTest {
    @Test fun installedGemmaGeneratesRealSummaries() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Model weights are missing or device is unsupported", LlmStatus.IDLE, LiteRtLlmSummarizer.installedStatus(app))
        val llm = LiteRtLlmSummarizer(app)
        val started = SystemClock.elapsedRealtime()
        try {
            llm.warmUp()
            val ready = withTimeout(150_000) { llm.status.first { it == LlmStatus.READY || it is LlmStatus.FAILED } }
            assertEquals("Native model failed: $ready", LlmStatus.READY, ready)
            val loadMs = SystemClock.elapsedRealtime() - started
            val context = SituationContext(
                events = listOf(SituationContext.Event(SoundType.DOORBELL, trend = Trend.STEADY, confidence = .95f, secondsAgo = 2.0)),
                speech = listOf(SituationContext.SpeechLine("Your package is here.", 1.0)),
            )
            val generationStart = SystemClock.elapsedRealtime()
            val summary = llm.summarize(context)
            assertNotNull("Model returned fallback; status=${llm.status.value}", summary)
            assertEquals(Source.LLM, summary!!.source)
            assertTrue(summary.text, Regex("(?i)door|package|delivery|courier").containsMatchIn(summary.text))
            assertTrue(summary.text, summary.text.split(Regex("\\s+")).size <= 15)
            val generationMs = SystemClock.elapsedRealtime() - generationStart
            val quiet = llm.summarize(SituationContext())
            assertNotNull("Second request failed; status=${llm.status.value}", quiet)
            assertEquals(Source.LLM, quiet!!.source)
            assertFalse("Conversation leaked earlier context: ${quiet.text}", Regex("(?i)package|courier|doorbell").containsMatchIn(quiet.text))
            val report = JSONObject().put("model", "Gemma 4 E2B").put("source", summary.source.name)
                .put("loadMs", loadMs).put("generationMs", generationMs).put("summary", summary.text).put("quietSummary", quiet.text)
            File(app.cacheDir, "llm-verification.json").writeText(report.toString(2))
            println("AWARE_LLM_VERIFIED $report")
        } finally { llm.close() }
    }
}
