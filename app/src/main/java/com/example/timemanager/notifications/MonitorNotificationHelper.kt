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
import com.example.timemanager.data.model.UsageLimitEvaluation

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
        limitEvaluation: UsageLimitEvaluation? = null,
        totalTrackedUsageSeconds: Long? = null,
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

        val title: String
        val message: String
        if (timerState != null) {
            title = timerState.appName
            val state = timerState
            val displayedSeconds = todayUsageSeconds ?: state.currentSessionSeconds
            message = if (limitEvaluation == null) {
                buildString {
                    append("今日：${formatDuration(displayedSeconds)}")
                    append("\n本次连续：${formatDuration(state.currentSessionSeconds)}")
                }
            } else {
                buildString {
                    append(
                        "今日 ${formatDuration(limitEvaluation.usedSeconds)} / " +
                            formatDuration(limitEvaluation.limitSeconds),
                    )
                    append("\n本次连续 ${formatDuration(state.currentSessionSeconds)}")
                    append("\n剩余 ${formatDuration(limitEvaluation.remainingSeconds)}")
                }
            }
        } else {
            title = "TimeManager 正在监控"
            message = if (totalTrackedUsageSeconds == null) {
                "TimeManager正在运行"
            } else {
                "今日受管理应用使用：${formatReadableDuration(totalTrackedUsageSeconds)}"
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
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
        limitEvaluation: UsageLimitEvaluation? = null,
    ): Boolean {
        return runCatching {
            context.getSystemService(NotificationManager::class.java).notify(
                NotificationIds.FOREGROUND_SERVICE,
                buildRunningNotification(timerState, todayUsageSeconds, limitEvaluation),
            )
            Log.d(TAG, "foreground notification active state=running")
        }.onFailure { error ->
            Log.w(TAG, "Unable to update foreground service notification.", error)
        }.isSuccess
    }

    fun updateWaitingNotification(totalTrackedUsageSeconds: Long): Boolean {
        return runCatching {
            context.getSystemService(NotificationManager::class.java).notify(
                NotificationIds.FOREGROUND_SERVICE,
                buildRunningNotification(totalTrackedUsageSeconds = totalTrackedUsageSeconds),
            )
            Log.d(TAG, "foreground notification active state=waiting")
        }.onFailure { error ->
            Log.w(TAG, "Unable to update waiting notification.", error)
        }.isSuccess
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun formatReadableDuration(totalSeconds: Long): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        return if (hours > 0L) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
    }

    companion object {
        private const val CHANNEL_ID = "usage_monitor_service"
        private const val CHANNEL_NAME = "实时使用监控"
        private const val CHANNEL_DESCRIPTION = "显示 TimeManager 实时监控的运行状态"
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L
        private const val TAG = "ForegroundNotification"
    }
}
