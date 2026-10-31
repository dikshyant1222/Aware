package com.aware.app.service

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real listening service and logs its state every second (tag AwareSmoke), for [SECONDS] seconds.
 * Run it while blocking and restoring microphone access from adb to watch the silence warning and recovery.
 * Passes if the service starts and the microphone delivered a signal at some point.
 */
@RunWith(AndroidJUnit4::class)
class ListeningSmokeTest {
    @Test fun serviceListensAndReportsItsState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val seconds = InstrumentationRegistry.getArguments().getString("seconds")?.toIntOrNull() ?: 20
        ListeningRepository.start(app)
        val started = SystemClock.elapsedRealtime()
        var heardSignal = false
        try {
            while (SystemClock.elapsedRealtime() - started < seconds * 1_000L) {
                val state = ListeningRepository.state.value
                if (state.levelDb > -80f) heardSignal = true
                Log.i("AwareSmoke", "t=${(SystemClock.elapsedRealtime() - started) / 1000}s running=${ListeningRepository.running.value} " +
                    "listening=${state.listening} level=${"%.0f".format(state.levelDb)}dB labels=${state.labels.size} error=${state.error}")
                Thread.sleep(1_000)
            }
        } finally {
            ListeningRepository.stop(app)
        }
        assertTrue("Microphone never delivered a signal", heardSignal)
    }
}
