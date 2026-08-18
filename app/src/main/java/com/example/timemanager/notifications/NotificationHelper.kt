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
        usedMinutes: Long,
    ): Boolean = sendNotification(
        notificationId = notificationId(packageName, APPROACHING_THRESHOLD),
        message = "$appName 已使用 $usedMinutes 分钟，接近每日限制",
    )

    fun sendLimitReached(
        packageName: String,
        appName: String,
    ): Boolean = sendNotification(
        notificationId = notificationId(packageName, REACHED_THRESHOLD),
        message = "$appName 今日额度已用完",
    )

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

    private companion object {
        const val CHANNEL_ID = "usage_limit_alerts"
        const val CHANNEL_NAME = "使用额度提醒"
        const val CHANNEL_DESCRIPTION = "应用接近或达到每日使用额度时提醒"
        const val APPROACHING_THRESHOLD = 80
        const val REACHED_THRESHOLD = 100
    }
}
