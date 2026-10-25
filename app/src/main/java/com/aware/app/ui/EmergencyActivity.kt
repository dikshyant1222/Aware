package com.aware.app.ui

import android.content.Intent
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.aware.app.R
import com.aware.app.alert.AlertMessages
import com.aware.app.alert.AlertReactor
import com.aware.app.alert.Category
import com.aware.app.alert.Detection
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import com.aware.app.haptics.HapticEvent
import com.aware.app.haptics.HapticPlayer
import com.aware.app.ui.theme.AwareTheme

class EmergencyActivity : ComponentActivity() {
    private lateinit var haptics: HapticPlayer
    private var sound by mutableStateOf(SoundType.SIREN)
    private var trend by mutableStateOf(Trend.STEADY)
    private var message by mutableStateOf("")
    private var animationsEnabled by mutableStateOf(false)
    private var active by mutableStateOf(true)
    private var deadlineMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        active = false
        haptics.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private val animationObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = readAnimationSetting()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        haptics = HapticPlayer(this)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!readIntent(intent)) {
            finish()
            return
        }
        deadlineMs = savedInstanceState?.getLong(STATE_DEADLINE) ?: (SystemClock.elapsedRealtime() + 30_000)
        setContent {
            AwareTheme {
                EmergencyContent(sound, message, trend, animationsEnabled && active, active, ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!readIntent(intent)) return
        setIntent(intent)
        deadlineMs = SystemClock.elapsedRealtime() + 30_000
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startAlert()
    }

    private fun readIntent(intent: Intent): Boolean {
        val type = SoundType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SOUND_TYPE) }
            ?.takeIf { it.category == Category.EMERGENCY } ?: return false
        sound = type
        trend = Trend.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TREND) } ?: Trend.STEADY
        message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
            ?: AlertMessages(::getString).templateFor(Detection(sound, 1f, trend, 0f, 0L, true))
        return true
    }

    override fun onStart() {
        super.onStart()
        readAnimationSetting()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationObserver,
        )
        if (!isFinishing) startAlert()
    }

    private fun startAlert() {
        handler.removeCallbacks(timeout)
        val remaining = deadlineMs - SystemClock.elapsedRealtime()
        active = remaining > 0
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            haptics.play(HapticEvent.from(sound), repeat = true)
            handler.postDelayed(timeout, remaining)
        } else {
            timeout.run()
        }
    }

    private fun readAnimationSetting() {
        animationsEnabled = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_DEADLINE, deadlineMs)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        handler.removeCallbacks(timeout)
        contentResolver.unregisterContentObserver(animationObserver)
        haptics.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun finish() {
        getSystemService(NotificationManager::class.java).cancel(AlertReactor.notificationId(Category.EMERGENCY))
        if (::haptics.isInitialized) haptics.stop()
        handler.removeCallbacks(timeout)
        super.finish()
    }

    companion object {
        const val EXTRA_SOUND_TYPE = "sound_type"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TREND = "trend"
        private const val STATE_DEADLINE = "deadline"
    }
}

@Composable
private fun EmergencyContent(sound: SoundType, message: String, trend: Trend, animate: Boolean, active: Boolean, dismiss: () -> Unit) {
    val deepRed = Color(0xFF600000)
    val background = if (animate) {
        val transition = rememberInfiniteTransition(label = "Emergency fade")
        // At most 0.67 flashes/s: 750 ms each way, smoothly fading, never a hard strobe.
        val color by transition.animateColor(
            initialValue = deepRed, targetValue = Color.Black,
            animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "Red to black",
        )
        color
    } else deepRed
    Column(
        modifier = Modifier.fillMaxSize().background(background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(Icons.Default.Warning, stringResource(R.string.category_emergency), Modifier.size(120.dp), tint = Color.White)
        Text(soundName(sound), color = Color.White, style = MaterialTheme.typography.displayMedium)
        Text(message, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(trendText(trend), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)) {
            Text(stringResource(R.string.emergency_dismiss), style = MaterialTheme.typography.titleLarge)
        }
        if (!active) Text(stringResource(R.string.emergency_timed_out), color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
