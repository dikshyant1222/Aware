package com.aware.app.haptics

import com.aware.app.alert.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    @Test
    fun everySoundTypeHasAnEvent() {
        SoundType.entries.forEach { assertEquals(it, HapticEvent.from(it).soundType) }
    }

    @Test
    fun everyAlertingEventVibratesWithinBounds() {
        for (event in HapticEvent.entries) {
            val pattern = patternFor(event) // the init block validates lengths, amplitudes and total
            if (event == HapticEvent.SPEECH) {
                assertTrue(pattern.timings.isEmpty())
            } else {
                assertTrue("$event never vibrates", pattern.amplitudes.any { it > 0 })
            }
        }
    }

    @Test
    fun namedPatternsMatchTheSpec() {
        val horn = patternFor(HapticEvent.CAR_HORN)
        assertEquals(2, horn.amplitudes.count { it == 255 })
        val siren = patternFor(HapticEvent.SIREN).amplitudes.filter { it > 0 }
        assertEquals(siren.sorted(), siren) // rising
        assertEquals(3, patternFor(HapticEvent.NAME_CALLED).amplitudes.count { it > 0 })
    }
}
