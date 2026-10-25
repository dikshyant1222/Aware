package com.aware.app.service

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aware.app.audio.AudioSource
import com.aware.app.classify.YamnetClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real pipeline and the real YAMNet model, with a fake microphone that fails, goes silent, then recovers. */
@RunWith(AndroidJUnit4::class)
class AwarePipelineRecoveryTest {
    private class FlakyMicrophone : AudioSource {
        override val sampleRate = 16_000
        @Volatile var starts = 0

        override fun windows(): Flow<FloatArray> = flow {
            starts++
            if (starts == 1) throw IllegalStateException("Microphone is busy")
            repeat(25) { emit(FloatArray(15_600)) } // exact silence, as Android returns when the mic is blocked
            while (true) {
                emit(FloatArray(15_600) { ((it * 7919L) % 2000 - 1000) / 20_000f }) // faint noise
                delay(40)
            }
        }
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) { if (condition()) return true; Thread.sleep(50) }
        return false
    }

    @Test fun captureFailureRetriesSilenceIsReportedAndAudioRecovers() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val microphone = FlakyMicrophone()
        val classifier = YamnetClassifier(app)
        val pipeline = AwarePipeline(microphone, classifier)
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            pipeline.start(scope)
            assertTrue("The failure was never reported", waitFor(5_000) { pipeline.state.value.error == "Microphone is busy" })
            assertEquals(false, pipeline.state.value.listening)
            assertTrue("Capture was not retried", waitFor(8_000) { microphone.starts >= 2 })
            assertTrue("Silence was not reported", waitFor(15_000) {
                pipeline.state.value.error?.contains("returning silence") == true
            })
            assertTrue("Did not recover when sound returned", waitFor(10_000) {
                pipeline.state.value.listening && pipeline.state.value.error == null
            })
            assertNull(pipeline.state.value.error)
        } finally {
            pipeline.stop()
            scope.cancel()
            classifier.close()
        }
    }
}
