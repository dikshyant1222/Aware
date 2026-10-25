package com.aware.app.demo

import com.aware.app.audio.AudioSource
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Produces paced PCM windows for the guided classifier fixture. No microphone is opened and
 * nothing is played through the speaker. The source remains active until the user stops it so
 * the detected alert stays visible for inspection.
 */
class DemoAudioSource(private val scenario: DemoScenario) : AudioSource {
    override val sampleRate: Int = 16_000

    override fun windows(): Flow<FloatArray> = flow {
        repeat(WINDOW_COUNT) { index ->
            emit(window(index))
            delay(HOP_DURATION_MS)
        }
        awaitCancellation()
    }

    private fun window(index: Int): FloatArray {
        val frequency = 320.0 + scenario.ordinal * 70.0
        return FloatArray(WINDOW_SIZE) { sample ->
            val phase = 2.0 * PI * frequency * (sample + index * HOP_SIZE) / sampleRate
            (sin(phase) * 0.18).toFloat()
        }
    }

    private companion object {
        const val WINDOW_SIZE = 15_600
        const val HOP_SIZE = 7_800
        const val HOP_DURATION_MS = 500L
        const val WINDOW_COUNT = 4
    }
}
