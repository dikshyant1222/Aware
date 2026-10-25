package com.aware.app.haptics

import com.aware.app.alert.Category
import com.aware.app.alert.SoundType

// The single editable configuration file for all Aware vibration patterns.
data class HapticPattern(val timings: List<Long>, val amplitudes: List<Int>) {
    init {
        require(timings.size == amplitudes.size)
        require(timings.all { it >= 0 } && timings.sum() <= 2_500)
        require(amplitudes.all { it in 0..255 })
    }
}

enum class HapticEvent(val soundType: SoundType?) {
    SIREN(SoundType.SIREN), SMOKE_ALARM(SoundType.SMOKE_ALARM),
    GLASS_BREAK(SoundType.GLASS_BREAK), SCREAM(SoundType.SCREAM),
    CAR_HORN(SoundType.CAR_HORN), VEHICLE(SoundType.VEHICLE),
    BICYCLE_BELL(SoundType.BICYCLE_BELL), DOORBELL(SoundType.DOORBELL),
    KNOCK(SoundType.KNOCK), PHONE_RING(SoundType.PHONE_RING),
    APPLIANCE_BEEP(SoundType.APPLIANCE_BEEP), WATER_RUNNING(SoundType.WATER_RUNNING),
    BABY_CRY(SoundType.BABY_CRY), SPEECH(SoundType.SPEECH),
    DOG_BARK(SoundType.DOG_BARK), CAT(SoundType.CAT),
    BIRD(SoundType.BIRD), NAME_CALLED(null), URGENT(null);

    val category: Category get() = if (this == URGENT) Category.EMERGENCY else soundType?.category ?: Category.PEOPLE

    companion object {
        fun from(type: SoundType): HapticEvent = entries.first { it.soundType == type }
    }
}

fun patternFor(event: HapticEvent): HapticPattern = when (event) {
    HapticEvent.SIREN, HapticEvent.SMOKE_ALARM, HapticEvent.SCREAM -> HapticPattern(
        listOf(400, 100, 400, 100, 400, 100, 400, 300),
        listOf(80, 0, 140, 0, 200, 0, 255, 0),
    )
    HapticEvent.GLASS_BREAK -> HapticPattern(listOf(180, 120, 65, 100, 65, 350), listOf(255, 0, 220, 0, 220, 0))
    HapticEvent.CAR_HORN -> HapticPattern(listOf(150, 100, 150, 350), listOf(255, 0, 255, 0))
    HapticEvent.VEHICLE -> HapticPattern(listOf(220, 150, 220, 350), listOf(120, 0, 160, 0))
    HapticEvent.BICYCLE_BELL -> HapticPattern(listOf(80, 100, 80, 350), listOf(110, 0, 150, 0))
    HapticEvent.BABY_CRY -> HapticPattern(
        listOf(90, 100, 140, 300, 90, 100, 140, 300, 90, 100, 140, 300),
        listOf(100, 0, 140, 0, 100, 0, 140, 0, 100, 0, 140, 0),
    )
    HapticEvent.DOORBELL, HapticEvent.KNOCK -> HapticPattern(
        listOf(60, 120, 60, 400, 60, 120, 60, 350), listOf(170, 0, 170, 0, 170, 0, 170, 0),
    )
    HapticEvent.PHONE_RING -> HapticPattern(listOf(220, 180, 220, 400), listOf(100, 0, 100, 0))
    HapticEvent.APPLIANCE_BEEP -> HapticPattern(listOf(100, 160, 100, 160, 100, 350), listOf(90, 0, 90, 0, 90, 0))
    HapticEvent.WATER_RUNNING -> HapticPattern(listOf(500, 350), listOf(65, 0))
    HapticEvent.DOG_BARK -> HapticPattern(listOf(70, 80, 110, 180, 70, 80, 110, 350), listOf(160, 0, 180, 0, 160, 0, 180, 0))
    HapticEvent.CAT -> HapticPattern(listOf(60, 90, 60, 350), listOf(120, 0, 120, 0))
    HapticEvent.BIRD -> HapticPattern(listOf(40, 60, 40, 60, 40, 350), listOf(70, 0, 70, 0, 70, 0))
    HapticEvent.NAME_CALLED -> HapticPattern(listOf(50, 80, 50, 80, 50, 350), listOf(80, 0, 80, 0, 80, 0))
    HapticEvent.URGENT -> HapticPattern(
        listOf(500, 100, 500, 100, 500, 100, 500, 100), listOf(255, 0, 255, 0, 255, 0, 255, 0),
    )
    HapticEvent.SPEECH ->HapticPattern(emptyList(), emptyList())
}
