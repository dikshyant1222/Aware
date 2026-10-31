package com.aware.app.llm

import com.aware.app.alert.SoundType
import java.util.concurrent.ConcurrentHashMap

/** Repeat alerts for background sounds the model judged LOW. Only ambient types can ever be muted. */
object AlertTriage {
    const val MUTE_MS = 60_000L
    private val ambient = setOf(SoundType.WATER_RUNNING, SoundType.APPLIANCE_BEEP, SoundType.VEHICLE, SoundType.DOG_BARK, SoundType.BIRD)
    private val mutedUntil = ConcurrentHashMap<SoundType, Long>()

    fun muteIfBackground(types: Collection<SoundType>, nowMs: Long) {
        if (types.isEmpty() || !ambient.containsAll(types)) return
        types.forEach { mutedUntil[it] = nowMs + MUTE_MS }
    }

    fun isMuted(type: SoundType, nowMs: Long): Boolean = type in ambient && (mutedUntil[type] ?: 0L) > nowMs

    /** Any non-background evidence lifts every mute. */
    fun clear() = mutedUntil.clear()
}
