package com.aware.app.classify

import java.io.Closeable

/** Classifies one mono PCM window. Implementations may use a model or a demo fixture. */
interface SoundClassifier : Closeable {
    fun classify(samples: FloatArray, sampleRate: Int): List<Label>
}
