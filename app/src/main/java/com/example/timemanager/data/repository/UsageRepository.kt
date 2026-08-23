package com.example.timemanager.data.repository

import android.os.SystemClock
import android.util.Log
import com.example.timemanager.data.model.AppUsage

/** Provides app-usage data to the presentation layer. */
class UsageRepository(
    private val usageStatsReader: UsageStatsReader,
) {
    fun getUsageSince(startTimeMillis: Long): List<AppUsage> =
        synchronized(CACHE_LOCK) {
            val now = SystemClock.elapsedRealtime()
            cachedUsage?.takeIf {
                cachedStartTimeMillis == startTimeMillis &&
                    now - cachedAtMillis < CACHE_DURATION_MILLIS
            }?.let {
                Log.d(TAG, "UsageStats cache hit age=${now - cachedAtMillis}ms")
                return it
            }

            val startedAt = SystemClock.elapsedRealtime()
            val loaded = mergeByPackage(usageStatsReader.getUsageSince(startTimeMillis))
            cachedStartTimeMillis = startTimeMillis
            cachedAtMillis = SystemClock.elapsedRealtime()
            cachedUsage = loaded
            Log.d(TAG, "UsageStats query=${cachedAtMillis - startedAt}ms apps=${loaded.size}")
            loaded
        }

    private fun mergeByPackage(usages: List<AppUsage>): List<AppUsage> = usages
        .fold(linkedMapOf<String, AppUsage>()) { usagesByPackage, appUsage ->
            val existingUsage = usagesByPackage[appUsage.packageName]
            usagesByPackage[appUsage.packageName] = if (existingUsage == null) {
                appUsage
            } else {
                existingUsage.copy(
                    usageTimeMillis = existingUsage.usageTimeMillis + appUsage.usageTimeMillis,
                    appIcon = existingUsage.appIcon ?: appUsage.appIcon,
                )
            }
            usagesByPackage
        }
        .values
        .sortedByDescending(AppUsage::usageTimeMillis)

    private companion object {
        const val TAG = "UsagePerformance"
        const val CACHE_DURATION_MILLIS = 5_000L
        val CACHE_LOCK = Any()
        var cachedStartTimeMillis: Long? = null
        var cachedAtMillis = 0L
        var cachedUsage: List<AppUsage>? = null
    }
}
