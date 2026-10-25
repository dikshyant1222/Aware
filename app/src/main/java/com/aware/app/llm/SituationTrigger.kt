package com.aware.app.llm

import com.aware.app.alert.SoundType
import com.aware.app.alert.Category
import kotlin.math.abs

/** Called only for a new alert or a newly final speech line, never on a timer. */
fun shouldAutoSummarize(type: SoundType, eventMs: Long, speechMs: Long, nowMs: Long, lastAutoMs: Long?): Boolean =
    type.alerts && type.category in setOf(Category.HOME, Category.PEOPLE, Category.EMERGENCY) &&
        nowMs - eventMs in 0..30_000L && nowMs - speechMs in 0..30_000L &&
        abs(eventMs - speechMs) <= 10_000L &&
        (lastAutoMs == null || nowMs - lastAutoMs >= 20_000L)
