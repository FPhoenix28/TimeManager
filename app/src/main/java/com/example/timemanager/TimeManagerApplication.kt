package com.example.timemanager

import android.app.Application
import android.util.Log
import com.example.timemanager.domain.todo.TodoRuntime
import com.example.timemanager.reminders.TodoReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeManagerApplication : Application() {
    private val todoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        TodoRuntime.get(this)
        todoScope.launch {
            try { TodoReminderScheduler(this@TimeManagerApplication).ensureScheduled() }
            catch (error: Exception) { Log.w("TodoReminder", "Initial schedule unavailable", error) }
        }
    }
}
