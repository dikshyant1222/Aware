package com.aware.app.llm

import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import org.junit.Assert.*
import org.junit.Test

class SituationRulesTest {
    private fun event(type: SoundType, trend: Trend = Trend.STEADY) = SituationContext.Event(type, type.category, trend, 0.9f, 1.0)
    private fun context(vararg events: SituationContext.Event, speech: String? = null) = SituationContext(
        events.toList(), listOfNotNull(speech?.let { SituationContext.SpeechLine(it, 1.0) }),
    )

    @Test fun quietIsLowAndRoutineSoundsAreNormal() {
        assertEquals(Urgency.LOW, SituationRules.floor(context()))
        assertEquals(Urgency.NORMAL, SituationRules.floor(context(event(SoundType.DOORBELL))))
    }
    @Test fun singleEmergencyIsHighAndCombinationsAreCritical() {
        assertEquals(Urgency.HIGH, SituationRules.floor(context(event(SoundType.SMOKE_ALARM))))
        assertEquals(Urgency.CRITICAL, SituationRules.floor(context(event(SoundType.SMOKE_ALARM), event(SoundType.SCREAM))))
        assertEquals(Urgency.CRITICAL, SituationRules.floor(context(event(SoundType.GLASS_BREAK), speech = "Help! Someone call 911")))
    }
    @Test fun distressWordsAloneAreNotCriticalWithoutAnEmergencySound() {
        assertEquals(Urgency.NORMAL, SituationRules.floor(context(event(SoundType.DOG_BARK), speech = "Help yourself to a snack")))
    }
    @Test fun hornWithApproachingVehicleIsHigh() {
        assertEquals(Urgency.HIGH, SituationRules.floor(context(event(SoundType.CAR_HORN), event(SoundType.VEHICLE, Trend.APPROACHING))))
        assertEquals(Urgency.NORMAL, SituationRules.floor(context(event(SoundType.CAR_HORN), event(SoundType.VEHICLE, Trend.FADING))))
    }
    @Test fun modelCanRaiseOnlyOneLevelAndNeverBelowAnEmergencyFloor() {
        assertEquals(Urgency.HIGH, SituationRules.resolve(Urgency.NORMAL, Urgency.CRITICAL, false))
        assertEquals(Urgency.CRITICAL, SituationRules.resolve(Urgency.HIGH, Urgency.CRITICAL, true))
        assertEquals(Urgency.HIGH, SituationRules.resolve(Urgency.HIGH, Urgency.LOW, true))
        assertEquals(Urgency.LOW, SituationRules.resolve(Urgency.NORMAL, Urgency.LOW, false))
        assertEquals(Urgency.NORMAL, SituationRules.resolve(Urgency.NORMAL, null, false))
    }
    @Test fun onlyAmbientSoundsCanBeMuted() {
        AlertTriage.clear()
        AlertTriage.muteIfBackground(listOf(SoundType.DOORBELL, SoundType.WATER_RUNNING), 0)
        assertFalse(AlertTriage.isMuted(SoundType.WATER_RUNNING, 1))
        AlertTriage.muteIfBackground(listOf(SoundType.DOG_BARK), 0)
        assertTrue(AlertTriage.isMuted(SoundType.DOG_BARK, 1))
        assertFalse(AlertTriage.isMuted(SoundType.DOG_BARK, AlertTriage.MUTE_MS + 1))
        assertFalse(AlertTriage.isMuted(SoundType.SIREN, 1))
        AlertTriage.clear()
    }
}
