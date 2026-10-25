package com.aware.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aware.app.MainActivity
import com.aware.app.service.ListeningRepository
import com.aware.app.service.PipelineState
import com.aware.app.speech.TranscriptEvent
import com.aware.app.speech.SharedAudioTranscriber
import com.aware.app.ui.theme.AwareTheme
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Production caption -> Compose -> WebView -> visible mesh -> native highlight. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class CaptionAnimationTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun openCaptions() {
        activityRule.scenario.onActivity {
            activity = it
            // Feed recognizer events deterministically without a second microphone.
            // The production screen, bridge, model, renderer, and queue are real.
            ListeningRepository.clearTranscript()
            ListeningRepository.update(PipelineState(listening = true), true)
            activity.setContent { AwareTheme { AslCaptionsScreen(onBack = {}) } }
        }
        waitUntil("avatar ready", 30_000) { diagnostics()?.optBoolean("ready") == true }
    }

    @After fun cleanUp() {
        activityRule.scenario.onActivity {
            it.setContent {}
            ListeningRepository.stopped()
            ListeningRepository.update(PipelineState(), false)
            ListeningRepository.clearTranscript()
        }
    }

    @Test fun partialSpeechMovesVisibleHandAndFinalDoesNotReplayIt() {
        instrumentation.waitForIdleSync()
        var before = captureAvatar()
        waitUntil("idle avatar rendered", 5_000) {
            before = captureAvatar()
            brightPixels(before) > 20_000
        }
        var lineId = 0L
        instrumentation.runOnMainSync {
            lineId = ListeningRepository.appendTranscript(TranscriptEvent("Hello", false, 1)).id
        }
        waitUntil("partial word starts signing", 5_000) { diagnostics()?.optString("currentId") == "$lineId:0" }
        javascript("window.setPlaybackPaused(true)")
        waitUntil("native caption highlight", 2_000) { hasHighlight("signing:0:5") }
        val first = requireNotNull(diagnostics())
        val joints = first.getJSONArray("joints").toString()
        javascript("window.setPlaybackPaused(false)")
        waitUntil("joint motion", 3_000) {
            val now = diagnostics()
            now != null && now.optDouble("time") > first.optDouble("time") + .05 &&
                now.getJSONArray("joints").toString() != joints
        }
        val moving = captureAvatar()
        saveImage("idle.png", before)
        saveImage("signing.png", moving)
        val changed = changedPixels(before, moving)
        assertTrue("Mesh stayed static: only $changed avatar pixels changed", changed > 1_000)
        waitUntil("partial word finishes", 5_000) { diagnostics()?.getJSONObject("stats")?.optInt("completed") == 1 }
        instrumentation.runOnMainSync {
            ListeningRepository.appendTranscript(TranscriptEvent("Hello", true, 2))
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, requireNotNull(diagnostics()).getJSONObject("stats").getInt("started"))
    }

    @Test fun growingPartialAndFinalMapEachWordToItsAnimationRange() {
        var lineId = 0L
        instrumentation.runOnMainSync {
            lineId = ListeningRepository.appendTranscript(TranscriptEvent("Hello thank", false, 1)).id
        }
        waitUntil("first partial word starts", 5_000) { diagnostics()?.optString("currentId") == "$lineId:0" }
        javascript("window.setPlaybackPaused(true)")
        instrumentation.runOnMainSync {
            ListeningRepository.appendTranscript(TranscriptEvent("Hello thank you", true, 2))
        }
        waitUntil("first word highlight", 2_000) { hasHighlight("signing:0:5") }
        javascript("window.setPlaybackPaused(false)")
        waitUntil("thank-you sign starts", 5_000) { diagnostics()?.optString("currentId") == "$lineId:1" }
        // Accessibility updates can lag a short clip on API 37. Hold this
        // checkpoint until the native range is read, then resume the same clip.
        javascript("window.setPlaybackPaused(true)")
        val range = requireNotNull(diagnostics()).getJSONArray("range")
        assertEquals(6, range.getInt(0))
        assertEquals(15, range.getInt(1))
        waitUntil("thank-you highlight", 2_000) { hasHighlight("signing:6:15") }
        javascript("window.setPlaybackPaused(false)")
        waitUntil("both words finish", 7_000) { diagnostics()?.getJSONObject("stats")?.optInt("completed") == 2 }
        assertEquals(2, requireNotNull(diagnostics()).getJSONObject("stats").getInt("started"))
        assertEquals(0, requireNotNull(diagnostics()).getJSONObject("stats").getInt("failed"))
    }

    @Test fun actualAndroidSpeechRecognitionFeedsTheSigner() = runBlocking {
        val audio = SpeechTestAudio.synthesize(instrumentation)
        val pcm = MutableSharedFlow<ShortArray>(extraBufferCapacity = 32)
        val events = ConcurrentLinkedQueue<TranscriptEvent>()
        val errors = ConcurrentLinkedQueue<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transcriber = SharedAudioTranscriber(activity, pcm, Locale.US) { code ->
            if (code != null) errors.add(code)
        }
        scope.launch(Dispatchers.Main.immediate) {
            transcriber.results.collect {
                events.add(it)
                ListeningRepository.appendTranscript(it)
            }
        }
        try {
            instrumentation.runOnMainSync { transcriber.start() }
            withTimeout(5_000) { pcm.subscriptionCount.first { it > 0 } }
            val input = ShortArray(8_000) + audio + ShortArray(48_000)
            val feeding = scope.launch {
                for (offset in input.indices step 320) {
                    pcm.emit(input.copyOfRange(offset, minOf(offset + 320, input.size)))
                    delay(20)
                }
            }
            waitUntil("Android recognized speech reaches animation; errors=$errors", 20_000) {
                events.any { it.text.contains("hello", ignoreCase = true) } &&
                    diagnostics()?.getJSONObject("stats")?.optInt("started", 0)?.let { it > 0 } == true
            }
            waitUntil("recognizer emits finalized segments; errors=$errors", 10_000) {
                events.any { it.isFinal && it.text.contains("hello", ignoreCase = true) }
            }
            feeding.cancelAndJoin()
            assertEquals(0, requireNotNull(diagnostics()).getJSONObject("stats").getInt("failed"))
        } finally {
            instrumentation.runOnMainSync { transcriber.release() }
            scope.cancel()
        }
    }

    @Test fun wordAndLetterHandoffsStayContinuousAtSixtyFramesPerSecond() {
        javascript("""
            window.transitionReport=null;
            (function(){
              const samples=[];let lastFrame=-1;const started=performance.now();
              function sample(now){
                const d=window.avatarDiagnostics();
                if(d.frames!==lastFrame){samples.push({at:now,id:d.currentId,joints:d.renderedJoints});lastFrame=d.frames}
                if(d.stats.completed>=4){
                  const seams=[],gaps=[];
                  const angle=(a,b)=>2*Math.acos(Math.min(1,Math.abs(a.reduce((sum,v,i)=>sum+v*b[i],0))));
                  for(let i=1;i<samples.length;i++){
                    const a=samples[i-1],b=samples[i];gaps.push(b.at-a.at);
                    if(a.id&&b.id&&a.id!==b.id)seams.push(Math.max(...a.joints.map((q,j)=>angle(q,b.joints[j]))));
                  }
                  gaps.sort((a,b)=>a-b);
                  window.transitionReport={seams,frames:samples.length,medianFrameMs:gaps[Math.floor(gaps.length*.5)],p95FrameMs:gaps[Math.floor(gaps.length*.95)],...d.transition};
                  return;
                }
                if(now-started>12000){window.transitionReport={error:'Playback timed out',...d};return}
                requestAnimationFrame(sample);
              }
              requestAnimationFrame(sample);
            })();
        """.trimIndent())
        instrumentation.runOnMainSync {
            ListeningRepository.appendTranscript(TranscriptEvent("Hello world thank you Zoe", true, 1))
        }
        waitUntil("continuous mixed signing sequence", 15_000) {
            javascript("window.transitionReport") is JSONObject
        }
        val report = javascript("window.transitionReport") as JSONObject
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        output?.let { File(it, "transitions.json").writeText(report.toString(2)) }
        assertFalse(report.toString(), report.has("error"))
        val seams = report.getJSONArray("seams")
        assertEquals("Missing a word handoff: $report", 3, seams.length())
        for (index in 0 until seams.length()) assertTrue("Pose snapped: $report", seams.getDouble(index) < .08)
        assertTrue("Playback missed its 60 fps cadence: $report", report.getDouble("medianFrameMs") < 22)
        assertTrue("Playback stuttered: $report", report.getDouble("p95FrameMs") < 40)
        assertTrue("Next clip was not prepared ahead: $report", report.getInt("seamCacheHits") >= 3)
        assertEquals(0, requireNotNull(diagnostics()).getJSONObject("stats").getInt("failed"))
    }

    private fun waitUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $label; ${diagnostics()}" }
            SystemClock.sleep(40)
        }
    }

    private fun diagnostics(): JSONObject? {
        val value = javascript("window.avatarDiagnostics?.()")
        return when (value) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> null
        }
    }

    private fun javascript(expression: String): Any? {
        val latch = CountDownLatch(1)
        var raw: String? = null
        instrumentation.runOnMainSync {
            val view = findWebView(activity.findViewById(android.R.id.content))
            if (view == null) latch.countDown() else view.evaluateJavascript(expression) {
                raw = it
                latch.countDown()
            }
        }
        check(latch.await(3, TimeUnit.SECONDS)) { "WebView did not answer diagnostics" }
        return raw?.let { JSONTokener(it).nextValue() }
    }

    private fun hasHighlight(description: String): Boolean {
        fun matches(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.refresh()
            if (node.stateDescription?.toString() == description) return true
            return (0 until node.childCount).any { matches(node.getChild(it)) }
        }
        return matches(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun captureAvatar(): Bitmap {
        var bounds = IntArray(4)
        instrumentation.runOnMainSync {
            val view = checkNotNull(findWebView(activity.findViewById(android.R.id.content)))
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            bounds = intArrayOf(location[0], location[1], view.width, view.height)
        }
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        return Bitmap.createBitmap(screen, bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun brightPixels(bitmap: Bitmap) = pixels(bitmap).count {
        Color.red(it) + Color.green(it) + Color.blue(it) > 120
    }

    private fun changedPixels(first: Bitmap, second: Bitmap): Int {
        val a = pixels(first)
        val b = pixels(second)
        assertEquals(a.size, b.size)
        return a.indices.count {
            kotlin.math.abs(Color.red(a[it]) - Color.red(b[it])) +
                kotlin.math.abs(Color.green(a[it]) - Color.green(b[it])) +
                kotlin.math.abs(Color.blue(a[it]) - Color.blue(b[it])) > 40
        }
    }

    private fun saveImage(name: String, bitmap: Bitmap) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = output?.let(::File) ?: checkNotNull(activity.getExternalFilesDir("sync-verification"))
        directory.mkdirs()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
