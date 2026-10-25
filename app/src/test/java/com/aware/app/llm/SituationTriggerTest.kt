package com.aware.app.llm

import com.aware.app.alert.SoundType
import org.junit.Assert.*
import org.junit.Test

class SituationTriggerTest {
    @Test fun tenSecondWindowEitherOrderInclusive() {
        assertTrue(shouldAutoSummarize(SoundType.KNOCK, 10_000, 20_000, 20_000, null))
        assertTrue(shouldAutoSummarize(SoundType.KNOCK, 20_000, 10_000, 20_000, null))
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 9_999, 20_000, 20_000, null))
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 20_000, 9_999, 20_000, null))
    }
    @Test fun twentySecondCooldownInclusive() {
        assertTrue(shouldAutoSummarize(SoundType.KNOCK, 30_000, 30_000, 30_000, 10_000))
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 30_000, 30_000, 30_000, 10_001))
    }
    @Test fun onlyHomePeopleAndEmergencyAlerts() {
        SoundType.entries.forEach { type ->
            val expected = type !in listOf(SoundType.SPEECH, SoundType.DOG_BARK, SoundType.VEHICLE, SoundType.CAR_HORN, SoundType.BICYCLE_BELL,
                SoundType.CAT, SoundType.FARM_ANIMAL, SoundType.BIRD)
            assertEquals(type.name, expected, shouldAutoSummarize(type, 10_000, 10_000, 10_000, null))
        }
    }
    @Test fun staleAndFutureTimesAreRejected() {
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 0, 1, 30_001, null))
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 20_001, 20_000, 20_000, null))
        assertFalse(shouldAutoSummarize(SoundType.KNOCK, 20_000, 20_001, 20_000, null))
    }
    @Test fun firstPairCanTriggerAtClockZero() {
        assertTrue(shouldAutoSummarize(SoundType.BABY_CRY, 0, 0, 0, null))
    }
}
