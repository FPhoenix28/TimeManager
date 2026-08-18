package com.example.timemanager.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.timemanager.MainActivity
import com.example.timemanager.R
import com.example.timemanager.data.model.TimerState

/** Creates the notification required while foreground app monitoring is active. */
class MonitorNotificationHelper(context: Context) {
    private val context = context.applicationContext

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun buildRunningNotification(
        timerState: TimerState? = null,
        todayUsageSeconds: Long? = null,
    ): Notification {
        createNotificationChannel()
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val message = timerState?.let { state ->
            val displayedSeconds = todayUsageSeconds ?: state.currentSessionSeconds
            "正在使用：${state.appName}\n今日：${formatDuration(displayedSeconds)}"
        } ?: "TimeManager正在运行"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TimeManager")
            .setContentText(message.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateRunningNotification(
        timerState: TimerState,
        todayUsageSeconds: Long,
    ) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildRunningNotification(timerState, todayUsageSeconds),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to update foreground service notification.", error)
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val NOTIFICATION_ID = 10_001
        private const val CHANNEL_ID = "usage_monitor_service"
        private const val CHANNEL_NAME = "实时使用监控"
        private const val CHANNEL_DESCRIPTION = "显示 TimeManager 实时监控的运行状态"
        private const val SECONDS_PER_MINUTE = 60L
        private const val TAG = "MonitorNotification"
    }
}
