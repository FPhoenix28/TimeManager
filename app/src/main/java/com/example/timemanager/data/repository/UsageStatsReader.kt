package com.example.timemanager.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.timemanager.data.model.AppUsage

/** Reads this device's app-usage statistics from Android usage events. */
class UsageStatsReader(
    private val context: Context,
) {
    private val installedAppInfoProvider = InstalledAppInfoProvider(context.packageManager)

    fun hasUsageAccess(): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageSince(startTimeMillis: Long): List<AppUsage> {
        return when (
            val result = getUsageBetween(
                startTimeMillis = startTimeMillis,
                endTimeMillis = System.currentTimeMillis(),
            )
        ) {
            is UsageStatsQueryResult.Success -> result.usages
            UsageStatsQueryResult.Unavailable -> emptyList()
        }
    }

    fun getUsageBetween(
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): UsageStatsQueryResult {
        if (!hasUsageAccess()) {
            Log.w(TAG, "Usage access is not granted; usage interval is unavailable.")
            return UsageStatsQueryResult.Unavailable
        }

        if (startTimeMillis >= endTimeMillis) {
            return UsageStatsQueryResult.Success(emptyList())
        }
        return runCatching {
            val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            val durationsByPackage = aggregateForegroundEvents(
                usageStatsManager = usageStatsManager,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
            )
            // An empty event window cannot prove zero use: the OS may no longer retain it.
            if (!durationsByPackage.hasEventsInRange && durationsByPackage.durations.isEmpty()) {
                Log.w(TAG, "No usage events available for start=$startTimeMillis end=$endTimeMillis")
                return@runCatching UsageStatsQueryResult.Unavailable
            }
            val appUsageList = durationsByPackage.durations
                .asSequence()
                .filter { (packageName, duration) ->
                    duration > 0L && packageName != context.packageName
                }
                .mapNotNull { (packageName, duration) ->
                    val installedApp = installedAppInfoProvider.getUserInstalledApp(packageName)
                        ?: return@mapNotNull null
                    AppUsage(
                        appName = installedApp.appName,
                        packageName = packageName,
                        usageTimeMillis = duration,
                        appIcon = installedApp.appIcon,
                    )
                }
                .sortedByDescending(AppUsage::usageTimeMillis)
                .toList()

            Log.d(
                TAG,
                buildString {
                    append("AppUsageList: start=")
                    append(startTimeMillis)
                    append(" end=")
                    append(endTimeMillis)
                    appUsageList.forEach { appUsage ->
                        append("\n")
                        append(appUsage.appName)
                        append(" ")
                        append(appUsage.usageTimeMillis / MILLIS_PER_MINUTE)
                        append("min")
                    }
                },
            )

            UsageStatsQueryResult.Success(appUsageList)
        }.getOrElse { error ->
            Log.e(
                TAG,
                "Unable to query usage interval start=$startTimeMillis end=$endTimeMillis",
                error,
            )
            UsageStatsQueryResult.Unavailable
        }
    }

    private fun aggregateForegroundEvents(
        usageStatsManager: UsageStatsManager,
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): AggregatedEvents {
        val events = usageStatsManager.queryEvents(
            (startTimeMillis - EVENT_LOOKBACK_MILLIS).coerceAtLeast(0L),
            endTimeMillis,
        )
        val event = UsageEvents.Event()
        val activeActivities = mutableSetOf<String>()
        val activeActivityCount = mutableMapOf<String, Int>()
        val packageSessionStart = mutableMapOf<String, Long>()
        val durations = mutableMapOf<String, Long>()
        var hasEventsInRange = false

        fun finishPackageSession(packageName: String, timestamp: Long) {
            val sessionStart = packageSessionStart.remove(packageName) ?: return
            val sessionEnd = timestamp.coerceIn(startTimeMillis, endTimeMillis)
            if (sessionEnd > sessionStart) {
                durations[packageName] =
                    (durations[packageName] ?: 0L) + (sessionEnd - sessionStart)
            }
        }

        fun finishAllSessions(timestamp: Long) {
            packageSessionStart.keys.toList().forEach { packageName ->
                finishPackageSession(packageName, timestamp)
            }
            activeActivities.clear()
            activeActivityCount.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp >= startTimeMillis && event.timeStamp < endTimeMillis) {
                hasEventsInRange = true
            }
            val packageName = event.packageName ?: continue
            val activityKey = "$packageName:${event.className.orEmpty()}"

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (activeActivities.add(activityKey)) {
                        val count = activeActivityCount[packageName] ?: 0
                        if (count == 0) {
                            packageSessionStart[packageName] =
                                event.timeStamp.coerceAtLeast(startTimeMillis)
                        }
                        activeActivityCount[packageName] = count + 1
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    if (activeActivities.remove(activityKey)) {
                        val remainingCount = (activeActivityCount[packageName] ?: 1) - 1
                        if (remainingCount <= 0) {
                            activeActivityCount.remove(packageName)
                            finishPackageSession(packageName, event.timeStamp)
                        } else {
                            activeActivityCount[packageName] = remainingCount
                        }
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> finishAllSessions(event.timeStamp)
            }
        }

        finishAllSessions(endTimeMillis)
        return AggregatedEvents(durations, hasEventsInRange)
    }

    private data class AggregatedEvents(
        val durations: Map<String, Long>,
        val hasEventsInRange: Boolean,
    )

    private companion object {
        const val TAG = "UsageStatsReader"
        const val MILLIS_PER_MINUTE = 60_000L
        const val EVENT_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
