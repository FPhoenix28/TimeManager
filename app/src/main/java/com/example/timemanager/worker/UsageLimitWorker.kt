package com.example.timemanager.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.notifications.NotificationHelper
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** Periodically checks today's usage against saved per-app limits. */
class UsageLimitWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val usageRepository = UsageRepository(UsageStatsReader(applicationContext))
    private val limitRepository = LimitRepository(AppLimitPreferences(applicationContext))
    private val notificationStateRepository = NotificationStateRepository(
        NotificationStatePreferences(applicationContext),
    )
    private val notificationHelper = NotificationHelper(applicationContext)

    override suspend fun doWork(): Result = runCatching {
        val appUsages = usageRepository.getTodayUsage()
        val limitsByPackage = limitRepository.observeAllLimits()
            .first()
            .associateBy(AppLimit::packageName)
        val today = LocalDate.now().toString()

        appUsages.forEach { appUsage ->
            val appLimit = limitsByPackage[appUsage.packageName] ?: return@forEach
            checkAndNotify(
                packageName = appUsage.packageName,
                appName = appUsage.appName,
                usageTimeMillis = appUsage.usageTimeMillis,
                limitMinutes = appLimit.limitMinutes,
                today = today,
            )
        }

        Result.success()
    }.getOrElse { error ->
        Log.e(TAG, "Usage limit check failed.", error)
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
    }

    private suspend fun checkAndNotify(
        packageName: String,
        appName: String,
        usageTimeMillis: Long,
        limitMinutes: Int,
        today: String,
    ) {
        val usedMinutes = usageTimeMillis / MILLIS_PER_MINUTE
        val limitMillis = limitMinutes.toLong() * MILLIS_PER_MINUTE
        val reachedLimit = if (limitMinutes <= 0) {
            usageTimeMillis > 0L
        } else {
            usageTimeMillis >= limitMillis
        }

        if (reachedLimit) {
            if (
                !notificationStateRepository.wasSentToday(
                    packageName,
                    REACHED_THRESHOLD,
                    today,
                ) && notificationHelper.sendLimitReached(packageName, appName)
            ) {
                notificationStateRepository.markSentToday(
                    packageName,
                    REACHED_THRESHOLD,
                    today,
                )
                notificationStateRepository.markSentToday(
                    packageName,
                    APPROACHING_THRESHOLD,
                    today,
                )
            }
            return
        }

        val approachingLimit = limitMinutes > 0 &&
            usageTimeMillis.toDouble() / limitMillis >= APPROACHING_RATIO
        if (
            approachingLimit &&
            !notificationStateRepository.wasSentToday(
                packageName,
                APPROACHING_THRESHOLD,
                today,
            ) && notificationHelper.sendApproachingLimit(
                packageName,
                appName,
                usedMinutes,
            )
        ) {
            notificationStateRepository.markSentToday(
                packageName,
                APPROACHING_THRESHOLD,
                today,
            )
        }
    }

    private companion object {
        const val TAG = "UsageLimitWorker"
        const val MILLIS_PER_MINUTE = 60_000L
        const val APPROACHING_THRESHOLD = 80
        const val REACHED_THRESHOLD = 100
        const val APPROACHING_RATIO = 0.8
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
