package com.example.timemanager.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.timemanager.data.model.ForegroundApp

/** Detects the most recently resumed application and resolves its display name. */
class ForegroundAppDetector(context: Context) {
    private val applicationContext = context.applicationContext
    private val usageStatsManager = applicationContext
        .getSystemService(UsageStatsManager::class.java)
    private val packageManager = applicationContext.packageManager
    private val appNameCache = mutableMapOf<String, String>()

    fun detect(
        beginTimeMillis: Long,
        endTimeMillis: Long,
    ): ForegroundApp? {
        if (beginTimeMillis >= endTimeMillis) return null

        return runCatching {
            val events = usageStatsManager.queryEvents(beginTimeMillis, endTimeMillis)
            val event = UsageEvents.Event()
            var latestPackageName: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestPackageName = event.packageName
                }
            }

            latestPackageName?.let { packageName ->
                Log.d(TAG, "foreground event packageName=$packageName")
                ForegroundApp(
                    packageName = packageName,
                    appName = resolveAppName(packageName),
                )
            }
        }.getOrElse { error ->
            Log.e(TAG, "无法检测当前前台应用。", error)
            null
        }
    }

    private fun resolveAppName(packageName: String): String = appNameCache.getOrPut(packageName) {
        runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrElse { error ->
            if (error !is PackageManager.NameNotFoundException) {
                Log.w(TAG, "无法读取应用名称: $packageName", error)
            }
            packageName
        }.ifBlank { packageName }
    }

    private companion object {
        const val TAG = "ForegroundAppDetector"
    }
}
