package com.example.timemanager.domain.todo

import android.content.Context
import android.util.Log
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.utils.todoUsageDays
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TodoRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
        CoroutineExceptionHandler { _, error -> Log.e("TodoRuntime", "Todo stream failed", error) })
    val repository = TodoRepository(AppDatabase.getInstance(context).todoDao(), todoUsageDays(context), scope)
    val summaryProvider = TodoSummaryProvider(repository, scope)

    companion object {
        @Volatile private var instance: TodoRuntime? = null
        fun get(context: Context): TodoRuntime = instance ?: synchronized(this) {
            instance ?: TodoRuntime(context.applicationContext).also { instance = it }
        }
    }
}
