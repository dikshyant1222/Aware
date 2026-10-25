package com.aware.app.alert

import com.aware.app.R

/** Resource lookup is supplied so the same templates can be verified on the JVM. */
class AlertMessages(private val getString: (Int) -> String) {
    fun templateFor(detection: Detection): String {
        val template = when (detection.type) {
            SoundType.SIREN -> R.string.alert_siren
            SoundType.SMOKE_ALARM -> R.string.alert_smoke_alarm
            SoundType.GLASS_BREAK -> R.string.alert_glass_break
            SoundType.SCREAM -> R.string.alert_scream
            SoundType.CAR_HORN -> R.string.alert_car_horn
            SoundType.VEHICLE -> R.string.alert_vehicle
            SoundType.BICYCLE_BELL -> R.string.alert_bicycle_bell
            SoundType.DOORBELL -> R.string.alert_doorbell
            SoundType.KNOCK -> R.string.alert_knock
            SoundType.PHONE_RING -> R.string.alert_phone_ring
            SoundType.APPLIANCE_BEEP -> R.string.alert_appliance_beep
            SoundType.WATER_RUNNING -> R.string.alert_water_running
            SoundType.BABY_CRY -> R.string.alert_baby_cry
            SoundType.SPEECH -> R.string.alert_speech
            SoundType.DOG_BARK -> R.string.alert_dog_bark
        }
        val trend = getString(when (detection.trend) {
            Trend.APPROACHING -> R.string.message_approaching
            Trend.STEADY -> R.string.message_steady
            Trend.FADING -> R.string.message_fading
        })
        return getString(template).format(trend)
    }
}
