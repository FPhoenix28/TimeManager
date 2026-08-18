package com.example.timemanager.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.timemanager.data.model.AppUsage

/** Reads this device's app-usage statistics for the current day. */
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
        if (!hasUsageAccess()) {
            Log.w(TAG, "Usage access is not granted; returning an empty usage list.")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        if (startTimeMillis >= now) return emptyList()
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val durationsByPackage = aggregateForegroundEvents(
            usageStatsManager = usageStatsManager,
            startTimeMillis = startTimeMillis,
            endTimeMillis = now,
        )

        val appUsageList = durationsByPackage
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
                append("AppUsageList:")
                appUsageList.forEach { appUsage ->
                    append("\n")
                    append(appUsage.appName)
                    append(" ")
                    append(appUsage.usageTimeMillis / MILLIS_PER_MINUTE)
                    append("min")
                }
            },
        )

        return appUsageList
    }

    private fun aggregateForegroundEvents(
        usageStatsManager: UsageStatsManager,
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): Map<String, Long> {
        val events = usageStatsManager.queryEvents(
            (startTimeMillis - EVENT_LOOKBACK_MILLIS).coerceAtLeast(0L),
            endTimeMillis,
        )
        val event = UsageEvents.Event()
        val activeActivities = mutableSetOf<String>()
        val activeActivityCount = mutableMapOf<String, Int>()
        val packageSessionStart = mutableMapOf<String, Long>()
        val durations = mutableMapOf<String, Long>()

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
        return durations
    }

    private companion object {
        const val TAG = "UsageStatsReader"
        const val MILLIS_PER_MINUTE = 60_000L
        const val EVENT_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
