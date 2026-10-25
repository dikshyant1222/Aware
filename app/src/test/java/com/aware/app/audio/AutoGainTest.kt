package com.aware.app.audio

import org.junit.Assert.*
import org.junit.Test

class AutoGainTest {
    private fun tone(amplitude: Float) = FloatArray(1_000) { if (it % 2 == 0) amplitude else -amplitude }

    @Test fun quietAudioIsLiftedTowardTheTargetButCapped() {
        val gain = AutoGain(release = 1f)
        val lifted = rms(gain.apply(tone(0.01f)))
        assertEquals(0.05f, lifted, 0.001f)
        assertEquals(0.002f * 8f, rms(gain.apply(tone(0.002f))), 0.001f)
    }
    @Test fun loudAudioIsNeverAttenuated() {
        val samples = tone(0.4f)
        assertSame(samples, AutoGain().apply(samples))
    }
    @Test fun silenceIsNotInflatedAndGainReleasesToUnity() {
        val gain = AutoGain(release = 1f)
        gain.apply(tone(0.01f))
        val silence = tone(0.0005f)
        assertSame(silence, gain.apply(silence))
    }
    @Test fun aSuddenLoudSoundAfterQuietDoesNotClip() {
        val gain = AutoGain(release = 1f)
        gain.apply(tone(0.01f))
        val loud = gain.apply(tone(0.5f))
        assertTrue(loud.all { it in -1f..1f })
        assertEquals(0.5f, rms(loud), 0.001f)
    }
}
