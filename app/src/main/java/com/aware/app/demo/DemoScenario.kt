package com.aware.app.demo

import com.aware.app.alert.SoundType

/** Reliable guided examples. Labels are exact YAMNet labels used by CategoryMapper. */
enum class DemoScenario(
    val soundType: SoundType,
    val classifierLabel: String,
) {
    DOORBELL(SoundType.DOORBELL, "Doorbell"),
    KNOCK(SoundType.KNOCK, "Knock"),
    SIREN(SoundType.SIREN, "Siren"),
    GLASS_BREAK(SoundType.GLASS_BREAK, "Shatter"),
    BABY_CRY(SoundType.BABY_CRY, "Baby cry, infant cry"),
    WATER_RUNNING(SoundType.WATER_RUNNING, "Water tap, faucet"),
}
