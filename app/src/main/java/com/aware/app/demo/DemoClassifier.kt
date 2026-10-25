package com.aware.app.demo

import com.aware.app.classify.Label
import com.aware.app.classify.SoundClassifier

/** Emits a stable classifier result so every guided demo is repeatable and offline. */
class DemoClassifier(private val scenario: DemoScenario) : SoundClassifier {
    override fun classify(samples: FloatArray, sampleRate: Int): List<Label> {
        require(samples.isNotEmpty()) { "A demo window must contain samples." }
        require(sampleRate > 0) { "Sample rate must be positive." }
        return listOf(Label(scenario.classifierLabel, DEMO_CONFIDENCE))
    }

    override fun close() = Unit

    private companion object {
        const val DEMO_CONFIDENCE = 0.92f
    }
}
