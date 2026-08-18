package com.example.timemanager.worker

import android.content.Context
import androidx.work.WorkManager

/** Removes the obsolete periodic check now replaced by foreground real-time evaluation. */
object UsageLimitWorkScheduler {
    fun cancelLegacySchedule(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private const val UNIQUE_WORK_NAME = "usage_limit_periodic_check"
}
