package com.aware.app.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aware.app.MainActivity
import com.aware.app.alert.*
import com.aware.app.llm.Source
import com.aware.app.llm.Summary
import com.aware.app.service.*
import com.aware.app.ui.theme.AwareTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real native navigation/actions, with deterministic repository data confined to tests. */
@RunWith(AndroidJUnit4::class)
class AwareUiTest {
    @get:Rule val rule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val now = System.currentTimeMillis()

    @Before fun prepare() {
        rule.scenario.onActivity {
            activity = it
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ListeningRepository.clearTranscript()
            ListeningRepository.update(PipelineState(), false)
        }
    }
    @After fun reset() {
        rule.scenario.onActivity {
            it.setContent {}
            ListeningRepository.stopped()
            ListeningRepository.update(PipelineState(), false)
            ListeningRepository.clearTranscript()
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun seed(count: Int = 6, summary: String? = null, alert: Boolean = false) {
        instrumentation.runOnMainSync {
            val types = listOf(SoundType.DOORBELL, SoundType.DOG_BARK, SoundType.BABY_CRY,
                SoundType.CAR_HORN, SoundType.WATER_RUNNING, SoundType.BICYCLE_BELL)
            ListeningRepository.update(PipelineState(listening = true, feed = types.take(count).mapIndexed { i, type ->
                FeedItem(now - i * 1000, type, .85f, Trend.STEADY)
            }, latestAlert = if (alert) Detection(SoundType.DOORBELL, .9f, Trend.STEADY, -30f, now, true) else null), true)
            if (summary != null) ListeningRepository.publishSituation(Summary(summary, Source.TEMPLATE), now)
        }
    }
    private fun show(width: Int = 390, scale: Float = 1f, permission: Boolean = false) {
        rule.scenario.onActivity {
            val density = activity.resources.displayMetrics.widthPixels.toFloat() / width
            it.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    AwareTheme { Box(Modifier.fillMaxSize()) {
                        if (permission) PermissionEntry() else PermissionScreen()
                    } }
                }
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun homeShowsTwoSoundsAndExpandsToFiveWithoutFiller() {
        seed()
        show()
        waitFor("home") { find("Surroundings") != null }
        assertNotNull(find("Doorbell"))
        assertNotNull(find("Dog barking"))
        assertNull(find("Baby crying"))
        assertNull(find("Test vibrations"))
        save("home-two-sounds")
        click("Show more")
        scrollTo("Running water")
        assertNull(find("Bicycle bell"))
        save("home-expanded")
        scrollTo("Show less", backwards = true)
        click("Show less")
        waitFor("collapsed sounds") { find("Show more") != null && find("Baby crying") == null }
    }

    @Test fun summarySheetAndDockNavigateWithoutDuplicatingRoutes() {
        val summary = "Someone is speaking nearby while water runs in the kitchen. A doorbell was heard a moment ago. " +
            "This longer summary remains fully readable in the sheet, even when the Home preview spans more than two lines."
        seed(2, summary)
        show()
        click(summary)
        waitFor("summary sheet") { find("Close") != null }
        assertNotNull(find(summary))
        save("summary-sheet")
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("sheet dismissed first") { find("Close") == null && find("Surroundings") != null }
        click("Settings")
        waitFor("settings groups") { find("Sound categories") != null }
        assertNull(find("Test vibrations"))
        save("settings")
        click("Home")
        waitFor("home restored") { find("Surroundings") != null }
        click("Live Talk")
        waitFor("live talk", 30000) { find("Waiting for speech…") != null }
        assertNull(find("Home"))
        assertNotNull(find("Back"))
        SystemClock.sleep(1500)
        save("live-talk")
        click("Back")
        waitFor("back from live talk") { find("Surroundings") != null }
    }

    @Test fun layoutFixturesAtSmallWidthsAndLargeFontsRemainNavigable() {
        seed(2, "Speech and a doorbell have been heard nearby.")
        for ((width, scale) in listOf(320 to 1f, 320 to 2f, 360 to 1.3f, 390 to 1f, 412 to 2f)) {
            show(width, scale)
            waitFor("home at $width/$scale") { find("Listening") != null }
            assertNotNull(find("Stop listening"))
            save("home-${width}-font-$scale")
            click("Settings")
            waitFor("settings at $width/$scale") { find("Sound categories") != null }
            save("settings-${width}-font-$scale")
            click("Home")
            waitFor("accessible Home dock at $width/$scale") { find("Listening") != null }
        }
    }

    @Test fun emptyAlertAndPermissionStatesUseTheSameVisualSystem() {
        show()
        waitFor("paused empty state") { find("Paused") != null }
        assertNotNull(find("A quiet moment"))
        assertNull(find("Show more"))
        save("home-empty")
        seed(1, alert = true)
        waitFor("last alert") { find("Last alert") != null }
        assertNotNull(find("Replay vibration"))
        save("home-alert")
        click("Alert details")
        waitFor("alert detail") { find("Close") != null }
        save("alert-sheet")
        click("Close")
        show(permission = true)
        waitFor("permission entry") { find("Stay connected to your surroundings.") != null }
        save("permission-entry")
    }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    }
    private fun find(label: String): AccessibilityNodeInfo? = nodes(instrumentation.uiAutomation.rootInActiveWindow)
        .firstOrNull { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }
    private fun click(label: String) {
        waitFor("clickable $label") { find(label) != null }
        var node = find(label)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("No click action for $label", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun scrollTo(label: String, backwards: Boolean = false) {
        repeat(8) {
            if (find(label) != null) return
            val action = if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            val scroll = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.isScrollable && it.isVisibleToUser }
            checkNotNull(scroll) { "No scroll surface for $label" }
            scroll.performAction(action)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
        }
        check(find(label) != null) { "Could not scroll to $label" }
    }
    private fun waitFor(label: String, timeout: Long = 5000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $label" }
            SystemClock.sleep(50)
        }
    }
    private fun save(name: String) {
        SystemClock.sleep(350)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: checkNotNull(activity.getExternalFilesDir("ui-verification"))
        output.mkdirs()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
