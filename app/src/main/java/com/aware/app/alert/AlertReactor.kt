package com.aware.app.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.aware.app.MainActivity
import com.aware.app.R
import com.aware.app.haptics.HapticEvent
import com.aware.app.haptics.HapticPlayer
import com.aware.app.llm.AlertTriage
import com.aware.app.llm.Urgency
import com.aware.app.ui.EmergencyActivity
import com.aware.app.ui.categoryColor

class AlertReactor(context: Context) {
    private val context = context.applicationContext
    private val haptics = HapticPlayer(this.context)
    private val messages = AlertMessages(this.context::getString)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)

    fun react(detection: Detection) {
        if (!detection.isNew || !detection.type.alerts) return
        if (AlertTriage.isMuted(detection.type, System.currentTimeMillis())) return
        haptics.play(HapticEvent.from(detection.type))
        val emergency = detection.type.category == Category.EMERGENCY
        val message = messages.templateFor(detection)
        val intent = if (emergency) {
            Intent(context, EmergencyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EmergencyActivity.EXTRA_SOUND_TYPE, detection.type.name)
                putExtra(EmergencyActivity.EXTRA_MESSAGE, message)
                putExtra(EmergencyActivity.EXTRA_TREND, detection.trend.name)
            }
        } else Intent(context, MainActivity::class.java)
        postNotification(detection.type.category, message, intent, detection.type.displayName)
        if (emergency && ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            try {
                context.startActivity(intent)
            } catch (_: SecurityException) {
                // Visibility can change during launch; the notification remains the fallback.
            }
        }
    }

    fun nameCalled(timeMs: Long) {
        haptics.play(HapticEvent.NAME_CALLED)
        val message = context.getString(R.string.alert_name_called)
        postNotification(Category.PEOPLE, message, Intent(context, MainActivity::class.java), message)
    }

    /**
     * A situation more serious than its individual alerts, such as smoke alarm plus screaming.
     * [silent] only refreshes the text of the notification already shown, without vibrating again.
     */
    fun escalate(urgency: Urgency, message: String, silent: Boolean) {
        if (!silent) haptics.play(HapticEvent.URGENT)
        postNotification(
            Category.EMERGENCY, message, Intent(context, MainActivity::class.java),
            context.getString(R.string.alert_urgent_title), ESCALATION_ID, urgency == Urgency.CRITICAL, silent,
        )
    }

    private fun postNotification(
        category: Category, message: String, intent: Intent, title: String,
        id: Int = notificationId(category), emergency: Boolean = category == Category.EMERGENCY, onlyAlertOnce: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        if (!notifications.areNotificationsEnabled()) return
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(categoryColor(category).toArgb())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setCategory(if (emergency) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (emergency) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
        try {
            if (emergency && (Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent())) {
                builder.setFullScreenIntent(pending, true)
            }
            notifications.notify(id, builder.build())
        } catch (_: SecurityException) {
            // A permission can be revoked between the check and notify; haptics still play.
        }
    }

    fun stop() = haptics.stop()

    companion object {
        private const val CHANNEL_ALERTS = "alerts"
        private const val ESCALATION_ID = 200

        fun notificationId(category: Category): Int = 100 + category.ordinal

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.alerts_channel),
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }
}
