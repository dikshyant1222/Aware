package com.aware.app.alert

import com.aware.app.classify.Label

enum class Trend { APPROACHING, STEADY, FADING }

data class Detection(
    val type: SoundType,
    val score: Float,
    val trend: Trend,
    val levelDb: Float,
    val timeMs: Long,
    val isNew: Boolean,
)

/**
 * A sound fires when it scores above its threshold in [CategoryMapper.requiredWindows] of its last
 * [CategoryMapper.windowSpan] windows, so one weak window in the middle doesn't reset it. It can optionally be
 * held for a settle period (default none); its confidence is the mean score while held, and only the
 * most confident sound per category is shown when several fire together.
 */
class AlertEngine(
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val emergencySettleMs: Long = EMERGENCY_SETTLE_MS,
) {
    var sensitivity: Float = 0.5f
        set(value) {
            require(value.isFinite()) { "Sensitivity must be finite." }
            field = value.coerceIn(0f, 1f)
        }
    var enabledCategories: Set<Category> = Category.entries.toSet()
        set(value) {
            field = value.toSet()
            hits.keys.retainAll(SoundType.entries.filter { it.category in field }.toSet())
            pending.keys.retainAll(SoundType.entries.filter { it.category in field }.toSet())
        }

    private class Pending(val dueMs: Long, val scores: MutableList<Float>)

    private var windowIndex = 0L
    private val hits = mutableMapOf<SoundType, ArrayDeque<Long>>()
    private val pending = mutableMapOf<SoundType, Pending>()
    private val lastFired = mutableMapOf<SoundType, Long>()
    private val levels = ArrayDeque<Pair<Long, Float>>()
    var trend: Trend = Trend.STEADY
        private set

    fun thresholdFor(type: SoundType): Float =
        CategoryMapper.minimumScore(type) * (1.4f - 0.8f * sensitivity)

    /** Call once per chronological audio window, including windows without mapped labels. */
    fun process(labels: List<Label>, levelDb: Float, nowMs: Long): List<Detection> {
        while (levels.isNotEmpty() && nowMs - levels.first().first > TREND_WINDOW_MS) {
            levels.removeFirst()
        }
        if (levelDb.isFinite()) levels.addLast(nowMs to levelDb)
        trend = calculateTrend()

        // Alias scores are correlated: take the best, never sum or count them as windows.
        val scores = mutableMapOf<SoundType, Float>()
        for (label in labels) {
            val type = CategoryMapper.map(label.name) ?: continue
            if (!label.score.isFinite() || label.score < thresholdFor(type)) continue
            if (type.category !in enabledCategories || !type.alerts) continue
            scores[type] = maxOf(scores[type] ?: 0f, label.score)
        }
        windowIndex++
        for (type in scores.keys) hits.getOrPut(type) { ArrayDeque() }.addLast(windowIndex)
        hits.entries.removeAll { (type, windows) ->
            val span = CategoryMapper.windowSpan(type)
            while (windows.isNotEmpty() && windows.first() <= windowIndex - span) windows.removeFirst()
            windows.isEmpty()
        }
        val detections = mutableListOf<Detection>()
        for ((type, score) in scores) {
            val emergency = type.category == Category.EMERGENCY
            val windows = CategoryMapper.requiredWindows(type)
            val streak = hits[type]?.size ?: 0
            val held = pending[type]
            if (held != null) {
                held.scores += score
                continue
            }
            val previous = lastFired[type]
            val cooldown = if (emergency) EMERGENCY_COOLDOWN_MS else DEFAULT_COOLDOWN_MS
            if (streak >= windows && (previous == null || nowMs - previous >= cooldown)) {
                val settle = if (emergency) emergencySettleMs else settleMs
                pending[type] = Pending(nowMs + settle, mutableListOf(score))
            } else if (previous != null) {
                detections += Detection(type, score, trend, levelDb, nowMs, isNew = false)
            }
        }
        // A transient sound may stop during the settle period; it still counts with its held scores.
        val due = pending.filterValues { nowMs >= it.dueMs }
        due.keys.forEach { pending.remove(it) }
        due.map { (type, held) -> type to held.scores.average().toFloat() }
            .groupBy { (type, _) -> type.category }
            .values
            .forEach { candidates ->
                val (best, confidence) = candidates.maxBy { it.second }
                // Weaker rivals in the same category start a cooldown so they don't fire right after.
                candidates.forEach { (type, _) -> lastFired[type] = nowMs }
                detections += Detection(best, confidence, trend, levelDb, nowMs, isNew = true)
            }
        return detections.sortedByDescending { it.score }
    }

    /** Start a fresh listening session while retaining the user's settings. */
    fun reset() {
        hits.clear()
        windowIndex = 0
        pending.clear()
        lastFired.clear()
        levels.clear()
        trend = Trend.STEADY
    }

    private fun calculateTrend(): Trend {
        if (levels.size < TREND_MIN_SAMPLES) return Trend.STEADY
        // Center seconds relative to the first timestamp to avoid precision loss at epoch times.
        val origin = levels.first().first
        val meanTime = levels.map { (it.first - origin) / 1_000.0 }.average()
        val meanLevel = levels.map { it.second.toDouble() }.average()
        var covariance = 0.0
        var variance = 0.0
        for ((time, level) in levels) {
            val delta = (time - origin) / 1_000.0 - meanTime
            covariance += delta * (level - meanLevel)
            variance += delta * delta
        }
        if (variance == 0.0) return Trend.STEADY
        val slope = covariance / variance
        return when {
            slope > TREND_SLOPE_DB_PER_SECOND -> Trend.APPROACHING
            slope < -TREND_SLOPE_DB_PER_SECOND -> Trend.FADING
            else -> Trend.STEADY
        }
    }

    companion object {
        const val DEFAULT_SETTLE_MS = 0L
        const val EMERGENCY_SETTLE_MS = 0L
        const val EMERGENCY_COOLDOWN_MS = 5_000L
        const val DEFAULT_COOLDOWN_MS = 8_000L
        const val TREND_WINDOW_MS = 4_000L
        const val TREND_MIN_SAMPLES = 4
        const val TREND_SLOPE_DB_PER_SECOND = 1.5
    }
}
