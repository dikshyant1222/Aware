package com.aware.app.alert

import com.aware.app.classify.Label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val siren = listOf(Label("Siren", 0.9f))
    private val bark = listOf(Label("Bark", 0.9f))
    private val car = listOf(Label("Car", 0.9f))

    private fun instant() = AlertEngine(settleMs = 0, emergencySettleMs = 0)

    @Test
    fun alertsWaitForTheSettlePeriodAndUseMeanConfidence() {
        val engine = AlertEngine(settleMs = 500)
        assertTrue(engine.process(listOf(Label("Doorbell", 0.4f)), -40f, 0).isEmpty())
        assertTrue(engine.process(listOf(Label("Doorbell", 0.6f)), -40f, 500).isEmpty()) // streak met, settling
        // The doorbell ended, but its held scores still release it once the settle period is over.
        val alert = engine.process(emptyList(), -40f, 1_000).single()
        assertEquals(SoundType.DOORBELL, alert.type)
        assertTrue(alert.isNew)
        assertEquals(0.6f, alert.score, 0.0001f)
    }

    @Test
    fun emergencyFiresWithoutSettling() {
        val engine = AlertEngine()
        assertTrue(engine.process(siren, -40f, 0).isEmpty())
        assertTrue(engine.process(siren, -40f, 500).single().isNew)
    }

    @Test
    fun onlyTheMostConfidentSoundPerCategoryIsShown() {
        val engine = AlertEngine(settleMs = 500)
        val home = listOf(Label("Bicycle bell", 0.5f), Label("Vehicle horn, car horn, honking", 0.9f))
        repeat(2) { engine.process(home, -40f, it * 500L) } // both pass at 500 ms, settle until 1 s
        val shown = engine.process(home + bark, -40f, 1_000)
        assertEquals(listOf(SoundType.CAR_HORN), shown.filter { it.isNew }.map { it.type })
        // The weaker bell went into cooldown along with it, so it doesn't fire right after.
        assertTrue(engine.process(home, -40f, 1_500).none { it.isNew })
    }

    @Test
    fun emergencyNeedsTwoConsecutiveWindows() {
        val engine = instant()
        assertTrue(engine.process(siren, -40f, 0).isEmpty())
        assertTrue(engine.process(siren, -40f, 500).single().isNew)
    }

    @Test
    fun glassBreakNeedsOnlyOneWindowAndKnocksNeedTwo() {
        val engine = instant()
        assertTrue(engine.process(listOf(Label("Shatter", 0.9f)), -40f, 0).single().isNew)
        val knock = listOf(Label("Knock", 0.4f))
        assertTrue(engine.process(knock, -40f, 500).isEmpty())
        assertEquals(SoundType.KNOCK, engine.process(knock, -40f, 1_000).single().type)
    }

    @Test
    fun burstySoundsNeedTwoHitsWithinFiveWindows() {
        val barking = instant()
        barking.process(bark, -40f, 0)
        barking.process(emptyList(), -40f, 500)
        barking.process(emptyList(), -40f, 1_000)
        assertEquals(SoundType.DOG_BARK, barking.process(bark, -40f, 1_500).single().type)
        val late = instant()
        late.process(bark, -40f, 0)
        repeat(4) { late.process(emptyList(), -40f, (it + 1) * 500L) }
        assertTrue(late.process(bark, -40f, 2_500).isEmpty()) // the first hit is five windows back
    }

    @Test
    fun babyCryCountsEitherCryLabelAtFullScore() {
        val cry = listOf(Label("Crying, sobbing", 0.4f))
        val engine = instant()
        assertTrue(engine.process(cry, -40f, 0).isEmpty())
        assertEquals(SoundType.BABY_CRY, engine.process(cry, -40f, 500).single().type)
        val infant = listOf(Label("Baby cry, infant cry", 0.4f))
        val other = instant()
        other.process(infant, -40f, 0)
        assertEquals(SoundType.BABY_CRY, other.process(infant, -40f, 500).single().type)
    }

    @Test
    fun animalSoundsAlertAndBirdsNeedAClearerCall() {
        val meow = listOf(Label("Meow", 0.5f))
        val cat = instant()
        cat.process(meow, -40f, 0)
        assertEquals(SoundType.CAT, cat.process(meow, -40f, 500).single().type)
        val moo = listOf(Label("Moo", 0.5f))
        val farm = instant()
        farm.process(moo, -40f, 0)
        assertEquals(SoundType.FARM_ANIMAL, farm.process(moo, -40f, 500).single().type)
        val faint = listOf(Label("Chirp, tweet", 0.35f))
        val quiet = instant()
        repeat(4) { assertTrue(quiet.process(faint, -40f, it * 500L).isEmpty()) }
        val tweet = listOf(Label("Chirp, tweet", 0.6f))
        val bird = instant()
        bird.process(tweet, -40f, 0)
        bird.process(tweet, -40f, 500)
        assertEquals(SoundType.BIRD, bird.process(tweet, -40f, 1_000).single().type)
    }

    @Test
    fun sustainedSoundsNeedThreeConsecutiveWindows() {
        val engine = instant()
        assertTrue(engine.process(car, -40f, 0).isEmpty())
        assertTrue(engine.process(car, -40f, 500).isEmpty())
        assertEquals(SoundType.VEHICLE, engine.process(car, -40f, 1_000).single().type)
    }

    @Test
    fun oneMissIsToleratedButTwoAreNot() {
        val engine = instant()
        engine.process(car, -40f, 0)
        engine.process(car, -40f, 500)
        engine.process(emptyList(), -40f, 1_000)
        assertTrue(engine.process(car, -40f, 1_500).single().isNew)

        val other = instant()
        other.process(car, -40f, 0)
        other.process(emptyList(), -40f, 500)
        other.process(listOf(Label("Car", 0.1f)), -40f, 1_000) // below threshold counts as a miss
        assertTrue(other.process(car, -40f, 1_500).isEmpty())
        assertTrue(other.process(car, -40f, 2_000).isEmpty())
        assertTrue(other.process(car, -40f, 2_500).single().isNew)
    }

    @Test
    fun aliasesCountAsOneWindowAndUseMaximumScore() {
        val engine = instant()
        val aliases = listOf(Label("Siren", 0.5f), Label("Civil defense siren", 0.8f))
        assertTrue(engine.process(aliases, -40f, 0).isEmpty())
        assertEquals(0.8f, engine.process(aliases, -40f, 500).single().score, 0f)
        val update = engine.process(aliases, -35f, 1_000).single()
        assertFalse(update.isNew)
        assertEquals(1_000L, update.timeMs)
        assertEquals(-35f, update.levelDb, 0f)
    }

    @Test
    fun emergencyCooldownUpdatesButDoesNotRefireUntilFiveSeconds() {
        val engine = instant()
        engine.process(siren, -40f, 0)
        engine.process(siren, -40f, 500)
        assertFalse(engine.process(siren, -38f, 5_499).single().isNew)
        assertTrue(engine.process(siren, -38f, 5_500).single().isNew)
    }

    @Test
    fun normalCooldownIsEightSecondsFromFiring() {
        val engine = instant()
        repeat(3) { engine.process(car, -40f, it * 500L) }
        assertFalse(engine.process(car, -40f, 8_999).single().isNew)
        assertTrue(engine.process(car, -40f, 9_000).single().isNew)
    }

    @Test
    fun cooldownExpiryStillNeedsEnoughRecentHits() {
        val engine = instant()
        engine.process(siren, -40f, 0)
        assertTrue(engine.process(siren, -40f, 500).single().isNew)
        for (time in 1_000L..5_000L step 500) engine.process(emptyList(), -40f, time)
        assertFalse(engine.process(siren, -40f, 5_500).single().isNew)
        assertTrue(engine.process(siren, -40f, 6_000).single().isNew)
    }

    @Test
    fun streaksAndCooldownsArePerSoundType() {
        val engine = instant()
        val both = siren + Label("Fire alarm", 0.8f)
        engine.process(both, -40f, 0)
        assertEquals(listOf(SoundType.SIREN), engine.process(both, -40f, 500).filter { it.isNew }.map { it.type })
        engine.process(car, -40f, 1_000)
        engine.process(car, -40f, 1_500)
        assertTrue(engine.process(car, -40f, 2_000).single().isNew)
    }

    @Test
    fun disabledCategoryNeverFiresAndEnablingRequiresFreshStreak() {
        val engine = instant()
        engine.process(siren, -40f, 0)
        engine.enabledCategories = setOf(Category.HOME)
        repeat(4) { assertTrue(engine.process(siren, -40f, (it + 1) * 500L).isEmpty()) }
        engine.enabledCategories = Category.entries.toSet()
        assertTrue(engine.process(siren, -40f, 2_500).isEmpty())
        assertTrue(engine.process(siren, -40f, 3_000).single().isNew)
        engine.enabledCategories = emptySet()
        assertTrue(engine.process(siren, -40f, 3_500).isEmpty())
    }

    @Test
    fun speechNeverCreatesAnAlertEvenAtMaximumSensitivity() {
        val engine = instant().apply { sensitivity = 1f }
        repeat(30) {
            assertTrue(engine.process(listOf(Label("Speech", 1f)), -40f, it * 500L).isEmpty())
        }
        assertFalse(SoundType.SPEECH.alerts)
    }

    @Test
    fun risingFallingAndFlatLevelsUseSecondsAndIrregularTimestamps() {
        for ((slope, expected) in listOf(2f to Trend.APPROACHING, -2f to Trend.FADING, 0f to Trend.STEADY)) {
            val engine = instant()
            var result: Detection? = null
            for (time in listOf(0L, 450L, 1_100L, 1_700L)) {
                result = engine.process(siren, -50f + slope * time / 1_000f, 1_700_000_000_000L + time)
                    .singleOrNull()
            }
            assertEquals(expected, result!!.trend)
            assertFalse(result.isNew)
        }
    }

    @Test
    fun fewerThanFourSamplesAndEqualTimestampsAreSteady() {
        val engine = instant()
        repeat(3) { engine.process(siren, -60f + it * 10f, it * 500L) }
        assertEquals(Trend.STEADY, engine.trend)
        engine.reset()
        repeat(4) { engine.process(siren, -60f + it * 10f, 0) }
        assertEquals(Trend.STEADY, engine.trend)
    }

    @Test
    fun samplesOlderThanFourSecondsAreDiscarded() {
        val engine = instant()
        repeat(4) { engine.process(siren, -60f + it * 2f, it * 500L) }
        assertEquals(Trend.APPROACHING, engine.trend)
        engine.process(siren, -80f, 6_000)
        assertEquals(Trend.STEADY, engine.trend)
    }

    @Test
    fun slopeAtTheBoundaryIsSteady() {
        for (slope in listOf(-1.5f, 1.5f)) {
            val engine = instant()
            repeat(4) { engine.process(siren, -50f + slope * it / 2, it * 500L) }
            assertEquals(Trend.STEADY, engine.trend)
        }
    }

    @Test
    fun sensitivityScalesThresholdsAndChangesFiring() {
        val high = instant().apply { sensitivity = 1f }
        val low = instant().apply { sensitivity = 0f }
        val normal = instant()
        assertEquals(0.40f, normal.thresholdFor(SoundType.VEHICLE), 0.0001f)
        assertEquals(0.24f, high.thresholdFor(SoundType.VEHICLE), 0.0001f)
        assertEquals(0.56f, low.thresholdFor(SoundType.VEHICLE), 0.0001f)
        val labels = listOf(Label("Car", 0.3f))
        var result = emptyList<Detection>()
        repeat(3) {
            result = high.process(labels, -40f, it * 500L)
            assertTrue(low.process(labels, -40f, it * 500L).isEmpty())
        }
        assertTrue(result.single().isNew)
    }

    @Test
    fun sensitivityIsClampedAndResetRetainsSettings() {
        val engine = instant()
        engine.sensitivity = 2f
        assertEquals(1f, engine.sensitivity, 0f)
        engine.sensitivity = -1f
        assertEquals(0f, engine.sensitivity, 0f)
        engine.enabledCategories = setOf(Category.EMERGENCY)
        engine.process(siren, -40f, 0)
        engine.process(siren, -40f, 500)
        engine.reset()
        assertEquals(setOf(Category.EMERGENCY), engine.enabledCategories)
        assertEquals(0f, engine.sensitivity, 0f)
        assertTrue(engine.process(siren, -40f, 1_000).isEmpty())
        assertTrue(engine.process(siren, -40f, 1_500).single().isNew)
    }
}
