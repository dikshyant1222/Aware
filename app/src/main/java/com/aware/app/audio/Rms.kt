package com.aware.app.audio

import kotlin.math.log10
import kotlin.math.sqrt

fun rms(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    var sum = 0.0
    for (sample in samples) {
        sum += sample.toDouble() * sample
    }
    return sqrt(sum / samples.size).toFloat()
}

fun toDbfs(rms: Float): Float =
    if (rms <= 0f) -90f else (20f * log10(rms)).coerceAtLeast(-90f)
