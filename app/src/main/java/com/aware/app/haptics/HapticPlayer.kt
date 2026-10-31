package com.aware.app.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.aware.app.alert.Category

class HapticPlayer(context: Context) {
    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Suppress("DEPRECATION")
    fun play(event: HapticEvent, repeat: Boolean = false) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val pattern = patternFor(event)
        if (pattern.timings.isEmpty()) return
        val amplitudes = pattern.amplitudes.map {
            if (it > 0 && !device.hasAmplitudeControl()) VibrationEffect.DEFAULT_AMPLITUDE else it
        }.toIntArray()
        val effect = VibrationEffect.createWaveform(pattern.timings.toLongArray(), amplitudes, if (repeat) 0 else -1)
        val emergency = event.category == Category.EMERGENCY
        if (Build.VERSION.SDK_INT >= 33) {
            device.vibrate(effect, VibrationAttributes.Builder().setUsage(
                if (emergency) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION,
            ).build())
        } else {
            device.vibrate(effect, AudioAttributes.Builder().setUsage(
                if (emergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION,
            ).build())
        }
    }

    fun stop() {
        vibrator?.cancel()
    }
}
