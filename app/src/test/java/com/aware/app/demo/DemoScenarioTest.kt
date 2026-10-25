package com.aware.app.demo

import com.aware.app.alert.CategoryMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioTest {
    @Test
    fun everyDemoLabelMapsToItsExpectedSound() {
        DemoScenario.entries.forEach { scenario ->
            assertEquals(scenario.soundType, CategoryMapper.map(scenario.classifierLabel))
        }
    }

    @Test
    fun scenariosDoNotRepeatSoundTypes() {
        val types = DemoScenario.entries.map { it.soundType }
        assertEquals(types.size, types.distinct().size)
        assertTrue(types.isNotEmpty())
    }

    @Test
    fun demoClassifierReturnsTheSelectedScenario() {
        val scenario = DemoScenario.DOORBELL
        val labels = DemoClassifier(scenario).classify(FloatArray(16_000), 16_000)

        assertEquals(1, labels.size)
        assertEquals(scenario.classifierLabel, labels.single().name)
        assertTrue(labels.single().score > 0.9f)
    }
}
