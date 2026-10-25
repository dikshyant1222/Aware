package com.aware.app.audio

/**
 * Lifts quiet audio toward a steady level so distant sirens and alarms reach the classifier at a usable
 * strength. It never attenuates, ignores near-silence so room noise isn't inflated, and drops back to
 * unity gain in silence. A louder input is handled on the same chunk, so a sudden siren doesn't clip.
 */
class AutoGain(
    private val target: Float = 0.05f,
    private val maxGain: Float = 8f,
    private val floor: Float = 0.002f,
    private val release: Float = 0.1f,
) {
    private var gain = 1f

    fun apply(samples: FloatArray): FloatArray {
        val level = rms(samples)
        if (level >= floor) {
            val desired = (target / level).coerceIn(1f, maxGain)
            gain = if (desired < gain) desired else gain + (desired - gain) * release
        } else {
            gain += (1f - gain) * release
        }
        if (gain <= 1.001f) return samples
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }
}
