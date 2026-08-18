package com.example.timemanager.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.timemanager.data.model.AppUsage
import java.util.Calendar

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

    fun getTodayUsage(): List<AppUsage> {
        if (!hasUsageAccess()) {
            Log.w(TAG, "Usage access is not granted; returning an empty usage list.")
            return emptyList()
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

        val appUsageList = usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfToday, now)
            .asSequence()
            .filter { it.totalTimeInForeground > 0L }
            .filter { it.packageName != context.packageName }
            .mapNotNull { usageStats ->
                val installedApp = installedAppInfoProvider
                    .getUserInstalledApp(usageStats.packageName)
                    ?: return@mapNotNull null

                AppUsage(
                    appName = installedApp.appName,
                    packageName = usageStats.packageName,
                    usageTimeMillis = usageStats.totalTimeInForeground,
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

    private companion object {
        const val TAG = "UsageStatsReader"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
