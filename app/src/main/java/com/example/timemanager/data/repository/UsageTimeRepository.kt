package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppUsage
import com.example.timemanager.data.model.RealtimeUsageRecord
import com.example.timemanager.data.model.TimerState
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import java.time.LocalDate
import kotlin.math.max

/** Combines UsageStats history with a persisted real-time usage estimate. */
class UsageTimeRepository(
    private val usageRepository: UsageRepository,
    private val preferences: RealtimeUsagePreferences,
) {
    private var runtimeDate: String? = null
    private var activePackageName: String? = null
    private var lastSessionSeconds = 0L
    private val estimatesByPackage = mutableMapOf<String, Long>()
    private val persistedSecondsByPackage = mutableMapOf<String, Long>()

    suspend fun recordTimerState(timerState: TimerState): Long {
        val today = today()
        ensureRuntimeDate(today)

        if (activePackageName != timerState.packageName) {
            flushActiveRecord(today)
            activePackageName = timerState.packageName
            lastSessionSeconds = timerState.currentSessionSeconds

            estimatesByPackage[timerState.packageName] = max(
                estimatesByPackage[timerState.packageName] ?: 0L,
                historicalUsageSeconds(timerState.packageName),
            )
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
            persist(timerState.packageName, today, estimate)
        }
        return estimate
    }

    /** Returns today's unified usage for one package in seconds. */
    suspend fun getTodayUsage(packageName: String): Long {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        val realtimeSeconds = preferences.getRecordsForDate(today())[packageName] ?: 0L
        return max(historicalUsageSeconds(packageName), realtimeSeconds)
    }

    /** Returns the home-screen list with unified usage values. */
    suspend fun getTodayUsage(): List<AppUsage> {
        val realtimeSeconds = preferences.getRecordsForDate(today())
        return usageRepository.getTodayUsage()
            .map { usage ->
                usage.copy(
                    usageTimeMillis = max(
                        usage.usageTimeMillis,
                        (realtimeSeconds[usage.packageName] ?: 0L) * MILLIS_PER_SECOND,
                    ),
                )
            }
            .sortedByDescending(AppUsage::usageTimeMillis)
    }

    suspend fun flush() {
        val date = runtimeDate ?: return
        flushActiveRecord(date)
    }

    private suspend fun ensureRuntimeDate(today: String) {
        if (runtimeDate == today) return

        val storedRecords = preferences.getRecordsForDate(today)
        runtimeDate = today
        activePackageName = null
        lastSessionSeconds = 0L
        estimatesByPackage.clear()
        estimatesByPackage.putAll(storedRecords)
        persistedSecondsByPackage.clear()
        persistedSecondsByPackage.putAll(storedRecords)
    }

    private suspend fun flushActiveRecord(date: String) {
        val packageName = activePackageName ?: return
        val estimate = estimatesByPackage[packageName] ?: return
        if (persistedSecondsByPackage[packageName] != estimate) {
            persist(packageName, date, estimate)
        }
    }

    private suspend fun persist(packageName: String, date: String, seconds: Long) {
        preferences.save(
            RealtimeUsageRecord(
                packageName = packageName,
                date = date,
                seconds = seconds,
            ),
        )
        persistedSecondsByPackage[packageName] = seconds
    }

    private fun historicalUsageSeconds(packageName: String): Long = usageRepository
        .getTodayUsage()
        .firstOrNull { usage -> usage.packageName == packageName }
        ?.usageTimeMillis
        ?.div(MILLIS_PER_SECOND)
        ?: 0L

    private fun today(): String = LocalDate.now().toString()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val PERSIST_INTERVAL_SECONDS = 5L
    }
}
