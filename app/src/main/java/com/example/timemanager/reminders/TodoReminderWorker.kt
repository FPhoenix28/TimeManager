package com.example.timemanager.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.timemanager.domain.todo.TodoReminderPolicy
import com.example.timemanager.domain.todo.TodoReminderRunner
import com.example.timemanager.domain.todo.TodoReminderStage
import com.example.timemanager.domain.todo.TodoRuntime
import com.example.timemanager.notifications.TodoNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TodoReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Renew before evaluating: even stale/empty workers keep future days scheduled.
            TodoReminderScheduler(applicationContext).ensureScheduled()
            val rawStage = inputData.getString(TodoReminderScheduler.STAGE)
            if (rawStage == TodoReminderScheduler.BOUNDARY) return@withContext Result.success()
            val stage = TodoReminderStage.entries.firstOrNull { it.name == rawStage }
                ?: return@withContext Result.failure()
            val day = inputData.getString(TodoReminderScheduler.DAY) ?: return@withContext Result.failure()
            val notifications = TodoNotificationHelper(applicationContext)
            val sent = TodoReminderRunner(TodoRuntime.get(applicationContext).repository,
                TodoReminderPolicy(), notifications::canPost, notifications::send).run(day, stage)
            Log.d("TodoReminder", "day=$day stage=$stage sent=$sent")
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("TodoReminder", "Reminder failed; existing todos are retained", error)
            Result.retry()
        }
    }
}
