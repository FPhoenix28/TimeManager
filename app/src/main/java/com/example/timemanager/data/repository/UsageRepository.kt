package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppUsage

/** Provides app-usage data to the presentation layer. */
class UsageRepository(
    private val usageStatsReader: UsageStatsReader,
) {
    fun getTodayUsage(): List<AppUsage> = usageStatsReader
        .getTodayUsage()
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
}
