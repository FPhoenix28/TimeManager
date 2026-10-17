package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppUsage
import com.example.timemanager.data.model.RealtimeUsageRecord
import com.example.timemanager.data.model.TimerState
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.utils.UsageDayProvider
import kotlin.math.max

/** Combines UsageStats history with a persisted real-time usage estimate. */
class UsageTimeRepository(
    private val usageRepository: UsageRepository,
    private val preferences: RealtimeUsagePreferences,
    private val historyRepository: UsageHistoryRepository,
    private val usageDayProvider: UsageDayProvider = UsageDayProvider(),
) {
    private var runtimeDate: String? = null
    private var activePackageName: String? = null
    private var lastSessionSeconds = 0L
    private val estimatesByPackage = mutableMapOf<String, Long>()
    private val persistedSecondsByPackage = mutableMapOf<String, Long>()
    private val historyPersistedSecondsByPackage = mutableMapOf<String, Long>()
    private var resumeWithoutSystemReseed: String? = null

    suspend fun recordTimerState(timerState: TimerState): Long {
        val usageDay = usageDayProvider.currentUsageDay()
        ensureRuntimeDate(usageDay.key)

        if (activePackageName != timerState.packageName) {
            flushActiveRecord(usageDay.key, forceHistory = true)
            activePackageName = timerState.packageName
            lastSessionSeconds = timerState.currentSessionSeconds

            if (resumeWithoutSystemReseed == timerState.packageName) {
                // The overlay covered this app: resume the flushed estimate, not OS foreground time.
                resumeWithoutSystemReseed = null
            } else {
                estimatesByPackage[timerState.packageName] = max(
                    estimatesByPackage[timerState.packageName] ?: 0L,
                    historicalUsageSeconds(timerState.packageName, usageDay.startTimeMillis),
                )
            }
        } else {
            val elapsedDelta = (timerState.currentSessionSeconds - lastSessionSeconds)
                .coerceAtLeast(0L)
            estimatesByPackage[timerState.packageName] =
                (estimatesByPackage[timerState.packageName] ?: 0L) + elapsedDelta
            lastSessionSeconds = timerState.currentSessionSeconds
        }

        val estimate = estimatesByPackage[timerState.packageName] ?: 0L
        val lastPersisted = persistedSecondsByPackage[timerState.packageName] ?: 0L
        if (estimate - lastPersisted >= PERSIST_INTERVAL_SECONDS) {
            persist(timerState.packageName, usageDay.key, estimate)
        }
        return estimate
    }

    /** Returns today's unified usage for one package in seconds. */
    suspend fun getTodayUsage(packageName: String): Long {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        val usageDay = usageDayProvider.currentUsageDay()
        val realtimeSeconds = preferences.getRecordsForDate(usageDay.key)[packageName] ?: 0L
        return max(
            historicalUsageSeconds(packageName, usageDay.startTimeMillis),
            realtimeSeconds,
        ).also { usageSeconds ->
            historyRepository.saveSnapshot(packageName, usageDay.key, usageSeconds)
        }
    }

    /** Returns the home-screen list with unified usage values. */
    suspend fun getTodayUsage(): List<AppUsage> {
        val usageDay = usageDayProvider.currentUsageDay()
        val realtimeSeconds = preferences.getRecordsForDate(usageDay.key)
        return usageRepository.getUsageSince(usageDay.startTimeMillis)
            .map { usage ->
                usage.copy(
                    usageTimeMillis = max(
                        usage.usageTimeMillis,
                        (realtimeSeconds[usage.packageName] ?: 0L) * MILLIS_PER_SECOND,
                    ),
                )
            }
            .sortedByDescending(AppUsage::usageTimeMillis)
            .also { usages ->
                usages.forEach { usage ->
                    historyRepository.saveSnapshot(
                        usage.packageName,
                        usageDay.key,
                        usage.usageTimeMillis / MILLIS_PER_SECOND,
                    )
                }
            }
    }

    suspend fun getTotalTodayUsageSeconds(packageNames: Set<String>): Long {
        if (packageNames.isEmpty()) return 0L

        val usageDay = usageDayProvider.currentUsageDay()
        val realtimeSeconds = preferences.getRecordsForDate(usageDay.key)
        val historicalMillis = usageRepository
            .getUsageSince(usageDay.startTimeMillis)
            .associate { usage -> usage.packageName to usage.usageTimeMillis }

        return packageNames.sumOf { packageName ->
            max(
                historicalMillis[packageName] ?: 0L,
                (realtimeSeconds[packageName] ?: 0L) * MILLIS_PER_SECOND,
            ) / MILLIS_PER_SECOND
        }
    }

    suspend fun flush() {
        val date = runtimeDate ?: return
        flushActiveRecord(date, forceHistory = true)
    }

    /** A restarted same-package physical session needs a fresh delta baseline. */
    suspend fun finishPhysicalSession(overlayPause: Boolean = false) {
        flush()
        if (overlayPause) resumeWithoutSystemReseed = activePackageName
        activePackageName = null
        lastSessionSeconds = 0L
    }

    fun currentRuntimeUsage(packageName: String): Long? =
        if (runtimeDate == usageDayProvider.currentUsageDay().key) estimatesByPackage[packageName] else null

    private suspend fun ensureRuntimeDate(today: String) {
        if (runtimeDate == today) return

        runtimeDate?.let { previousDate ->
            flushActiveRecord(previousDate, forceHistory = true)
        }

        val storedRecords = preferences.getRecordsForDate(today)
        runtimeDate = today
        activePackageName = null
        resumeWithoutSystemReseed = null
        lastSessionSeconds = 0L
        estimatesByPackage.clear()
        estimatesByPackage.putAll(storedRecords)
        persistedSecondsByPackage.clear()
        persistedSecondsByPackage.putAll(storedRecords)
        historyPersistedSecondsByPackage.clear()
        historyPersistedSecondsByPackage.putAll(storedRecords)
    }

    private suspend fun flushActiveRecord(date: String, forceHistory: Boolean = false) {
        val packageName = activePackageName ?: return
        val estimate = estimatesByPackage[packageName] ?: return
        if (
            persistedSecondsByPackage[packageName] != estimate ||
            forceHistory && historyPersistedSecondsByPackage[packageName] != estimate
        ) {
            persist(packageName, date, estimate, forceHistory)
        }
    }

    private suspend fun persist(
        packageName: String,
        date: String,
        seconds: Long,
        forceHistory: Boolean = false,
    ) {
        preferences.save(
            RealtimeUsageRecord(
                packageName = packageName,
                date = date,
                seconds = seconds,
            ),
        )
        persistedSecondsByPackage[packageName] = seconds
        val historyPersisted = historyPersistedSecondsByPackage[packageName] ?: 0L
        if (forceHistory || seconds - historyPersisted >= HISTORY_PERSIST_INTERVAL_SECONDS) {
            historyRepository.saveSnapshot(packageName, date, seconds)
            historyPersistedSecondsByPackage[packageName] = seconds
        }
    }

    private fun historicalUsageSeconds(
        packageName: String,
        startTimeMillis: Long,
    ): Long = usageRepository
        .getUsageSince(startTimeMillis)
        .firstOrNull { usage -> usage.packageName == packageName }
        ?.usageTimeMillis
        ?.div(MILLIS_PER_SECOND)
        ?: 0L

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val PERSIST_INTERVAL_SECONDS = 5L
        const val HISTORY_PERSIST_INTERVAL_SECONDS = 30L
    }
}
