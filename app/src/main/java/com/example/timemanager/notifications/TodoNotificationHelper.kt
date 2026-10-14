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
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.domain.todo.TodoReminderStage

class TodoNotificationHelper(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "待办提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "一天中的轻量待办提醒"
        })
    }

    fun canPost(): Boolean {
        createChannel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun send(stage: TodoReminderStage, todos: List<TodoEntity>) {
        val title = when (stage) {
            TodoReminderStage.AFTERNOON -> "今天还有 ${todos.size} 项待办"
            TodoReminderStage.EVENING -> "今晚还有 ${todos.size} 项待办"
            TodoReminderStage.LATE -> "今天还有 ${todos.size} 项待办未完成"
        }
        val preview = todos.take(3).joinToString(" · ") { it.title }
        val message = preview + if (todos.size > 3) " · 还有 ${todos.size - 3} 项" else ""
        val open = PendingIntent.getActivity(app, NOTIFICATION_ID,
            Intent(app, MainActivity::class.java).apply {
                action = ACTION_OPEN_TODOS
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TimeManager · 今日待办")
            .setContentText(title).setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$message"))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
    }

    companion object {
        const val ACTION_OPEN_TODOS = "com.example.timemanager.action.OPEN_TODOS"
        const val CHANNEL = "todo_reminders"
        // Dedicated space; existing foreground / daily / session / focus IDs remain unchanged.
        const val NOTIFICATION_ID = 50_001
    }
}
