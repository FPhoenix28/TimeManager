package com.example.timemanager.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.timemanager.domain.todo.TodoReminderPolicy
import com.example.timemanager.utils.UsageDayProvider
import java.util.concurrent.TimeUnit

class TodoReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Called on IO; durable unique work survives process death and ordinary device restarts. */
    fun ensureScheduled() {
        val policy = TodoReminderPolicy()
        policy.plan().forEach { window ->
            val request = OneTimeWorkRequestBuilder<TodoReminderWorker>()
                .setInitialDelay(window.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(DAY to window.usageDay, STAGE to window.stage.name))
                .addTag(TAG).build()
            workManager.enqueueUniqueWork(window.workName, ExistingWorkPolicy.KEEP, request).result.get()
        }
        val day = UsageDayProvider().currentUsageDay()
        val boundary = OneTimeWorkRequestBuilder<TodoReminderWorker>()
            .setInitialDelay((day.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DAY to day.key, STAGE to BOUNDARY))
            .addTag(TAG).build()
        workManager.enqueueUniqueWork("todo_boundary_${day.key}", ExistingWorkPolicy.KEEP, boundary).result.get()
    }

    companion object {
        const val DAY = "todo_usage_day"
        const val STAGE = "todo_stage"
        const val BOUNDARY = "BOUNDARY"
        const val TAG = "todo_reminders"
    }
}
