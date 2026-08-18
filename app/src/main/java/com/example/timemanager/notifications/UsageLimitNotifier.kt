package com.example.timemanager.notifications

import com.example.timemanager.data.model.UsageLimitEvaluation
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.utils.UsageDayProvider

/** Sends threshold alerts once per app and 04:00-based usage day. */
class UsageLimitNotifier(
    private val stateRepository: NotificationStateRepository,
    private val notificationHelper: NotificationHelper,
    private val usageDayProvider: UsageDayProvider,
) {
    private var cachedUsageDay: String? = null
    private val sentThresholdCache = mutableSetOf<String>()

    suspend fun notifyIfNeeded(
        appName: String,
        evaluation: UsageLimitEvaluation,
    ) {
        val usageDayKey = usageDayProvider.currentUsageDay().key
        resetCacheIfNeeded(usageDayKey)

        if (evaluation.isLimitReached) {
            if (!wasSent(evaluation.packageName, REACHED_THRESHOLD, usageDayKey)) {
                val sent = notificationHelper.sendLimitReached(
                    evaluation.packageName,
                    appName,
                )
                if (sent) {
                    markSent(evaluation.packageName, REACHED_THRESHOLD, usageDayKey)
                    markSent(evaluation.packageName, WARNING_THRESHOLD, usageDayKey)
                }
            }
            return
        }

        if (
            evaluation.hasReachedWarning &&
            !wasSent(evaluation.packageName, WARNING_THRESHOLD, usageDayKey)
        ) {
            val sent = notificationHelper.sendApproachingLimit(
                packageName = evaluation.packageName,
                appName = appName,
                remainingSeconds = evaluation.remainingSeconds,
            )
            if (sent) {
                markSent(evaluation.packageName, WARNING_THRESHOLD, usageDayKey)
            }
        }
    }

    private suspend fun wasSent(
        packageName: String,
        threshold: Int,
        usageDayKey: String,
    ): Boolean {
        val cacheKey = "$threshold:$packageName"
        if (cacheKey in sentThresholdCache) return true

        val wasSent = stateRepository.wasSentToday(packageName, threshold, usageDayKey)
        if (wasSent) sentThresholdCache += cacheKey
        return wasSent
    }

    private suspend fun markSent(
        packageName: String,
        threshold: Int,
        usageDayKey: String,
    ) {
        stateRepository.markSentToday(packageName, threshold, usageDayKey)
        sentThresholdCache += "$threshold:$packageName"
    }

    private fun resetCacheIfNeeded(usageDayKey: String) {
        if (cachedUsageDay == usageDayKey) return
        cachedUsageDay = usageDayKey
        sentThresholdCache.clear()
    }

    private companion object {
        const val WARNING_THRESHOLD = 80
        const val REACHED_THRESHOLD = 100
    }
}
