package com.example.timemanager.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Registers one periodic usage-limit check for this application. */
object UsageLimitWorkScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageLimitWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    private const val UNIQUE_WORK_NAME = "usage_limit_periodic_check"
    private const val REPEAT_INTERVAL_MINUTES = 15L
}
