package com.example.timemanager.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.timemanager.MainActivity
import com.example.timemanager.R
import com.example.timemanager.data.model.SessionReminder

/** Creates the usage-alert channel and posts usage limit notifications. */
class NotificationHelper(context: Context) {
    private val context = context.applicationContext

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun sendApproachingLimit(
        packageName: String,
        appName: String,
        remainingSeconds: Long,
    ): Boolean = sendNotification(
        notificationId = notificationId(packageName, APPROACHING_THRESHOLD),
        message = "$appName 今日额度已使用 80%，还剩 ${remainingMinutes(remainingSeconds)} 分钟",
    )

    fun sendLimitReached(
        packageName: String,
        appName: String,
    ): Boolean = sendNotification(
        notificationId = notificationId(packageName, REACHED_THRESHOLD),
        message = "$appName 今日额度已用完",
    )

    fun sendSessionReminder(
        packageName: String,
        appName: String,
        reminder: SessionReminder,
    ): Boolean {
        val message = when (reminder) {
            SessionReminder.FIFTEEN_MINUTES ->
                "你已经连续使用$appName 15 分钟"
            SessionReminder.THIRTY_MINUTES ->
                "你已经连续使用$appName 30 分钟，建议暂时离开一下"
        }
        return sendNotification(
            notificationId = notificationId(
                packageName,
                SESSION_NOTIFICATION_OFFSET + reminder.thresholdMinutes,
            ),
            message = message,
        )
    }

    private fun sendNotification(notificationId: Int, message: String): Boolean {
        if (!canPostNotifications()) return false
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TimeManager")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(packageName: String, thresholdPercent: Int): Int =
        31 * packageName.hashCode() + thresholdPercent

    private fun remainingMinutes(remainingSeconds: Long): Long =
        (remainingSeconds.coerceAtLeast(0L) + SECONDS_PER_MINUTE - 1L) / SECONDS_PER_MINUTE

    private companion object {
        const val CHANNEL_ID = "usage_limit_alerts"
        const val CHANNEL_NAME = "使用额度提醒"
        const val CHANNEL_DESCRIPTION = "应用接近或达到每日使用额度时提醒"
        const val APPROACHING_THRESHOLD = 80
        const val REACHED_THRESHOLD = 100
        const val SECONDS_PER_MINUTE = 60L
        const val SESSION_NOTIFICATION_OFFSET = 1_000
    }
}
