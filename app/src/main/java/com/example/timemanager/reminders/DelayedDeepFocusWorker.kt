package com.example.timemanager.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

class DelayedDeepFocusWorker internal constructor(
    context: Context,
    parameters: WorkerParameters,
    private val scheduler: DelayedDeepFocusScheduler,
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters, DelayedDeepFocusScheduler.get(context))

    override suspend fun doWork(): Result = try {
        val reason = scheduler.startWhenEligible(id, inputData.getLong(DelayedDeepFocusScheduler.EXPECTED_START, 0L))
        if (reason == null) Result.success()
        else {
            Log.i("DelayedDeepFocus", reason)
            Result.failure(workDataOf(DelayedDeepFocusScheduler.FAILURE_REASON to reason))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w("DelayedDeepFocus", "Automatic start failed; no retry scheduled", error)
        Result.failure(workDataOf(DelayedDeepFocusScheduler.FAILURE_REASON to "延迟静心未启动：模式发生变化或保存失败，请重新检查。"))
    }
}
