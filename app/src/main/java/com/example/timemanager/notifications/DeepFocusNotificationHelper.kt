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
import androidx.core.content.ContextCompat
import com.example.timemanager.MainActivity
import com.example.timemanager.R
import com.example.timemanager.data.model.FocusModeState
import java.text.DateFormat
import java.util.Date

class DeepFocusNotificationHelper(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(state: FocusModeState): Boolean {
        if (!canPostNotifications()) return false
        createChannel()
        val openDeepFocus = PendingIntent.getActivity(
            context,
            NotificationIds.DEEP_FOCUS,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_DEEP_FOCUS
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (state.isDeepFocusCompleted()) {
            "静心结束 · 点击完成"
        } else {
            "静心进行中 · 结束时间 " +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.endTimeMillis))
        }
        manager.notify(
            NotificationIds.DEEP_FOCUS,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("TimeManager")
                .setContentText(message)
                .setContentIntent(openDeepFocus)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build(),
        )
        return true
    }

    fun cancel() {
        manager.cancel(NotificationIds.DEEP_FOCUS)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "静心模式",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示静心模式的结束时间和恢复入口"
                setShowBadge(false)
            },
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_OPEN_DEEP_FOCUS = "com.example.timemanager.action.OPEN_DEEP_FOCUS"
        private const val CHANNEL_ID = "deep_focus_status"
    }
}
