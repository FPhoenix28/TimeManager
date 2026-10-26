package com.example.timemanager.ui.home

import com.example.timemanager.data.model.AppUsage
import com.example.timemanager.data.model.InstalledApp

/** Home list membership is management, not whether UsageStats has a row today. */
internal fun managedHomeUsage(
    usages: List<AppUsage>,
    managedApps: List<InstalledApp>,
): List<AppUsage> {
    val usageByPackage = usages.associateBy(AppUsage::packageName)
    return managedApps.map { app ->
        val usage = usageByPackage[app.packageName]
        AppUsage(
            appName = app.appName,
            packageName = app.packageName,
            usageTimeMillis = usage?.usageTimeMillis ?: 0L,
            appIcon = app.appIcon ?: usage?.appIcon,
        )
    }.sortedByDescending(AppUsage::usageTimeMillis)
}
