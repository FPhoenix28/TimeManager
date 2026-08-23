package com.example.timemanager.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.example.timemanager.data.model.InstalledApp

/** Resolves display information for user-installed applications. */
class InstalledAppInfoProvider(
    private val packageManager: PackageManager,
) {
    fun getUserInstalledApp(packageName: String): InstalledApp? {
        synchronized(CACHE_LOCK) {
            appCache[packageName]?.let { return it }
            if (packageName in unavailablePackages) return null
        }
        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse { error ->
            Log.w(
                TAG,
                "Skipping $packageName because its application info is unavailable or not visible.",
                error,
            )
            synchronized(CACHE_LOCK) { unavailablePackages += packageName }
            return null
        }

        return applicationInfo.toInstalledApp().also { installedApp ->
            synchronized(CACHE_LOCK) {
                if (installedApp == null) unavailablePackages += packageName
                else appCache[packageName] = installedApp
            }
        }
    }

    fun getUserInstalledApps(): List<InstalledApp> {
        val now = SystemClock.elapsedRealtime()
        synchronized(CACHE_LOCK) {
            cachedInstalledApps?.takeIf {
                now - installedAppsCachedAtMillis < INSTALLED_APPS_CACHE_MILLIS
            }?.let { return it }
        }
        val startedAt = SystemClock.elapsedRealtime()
        val loadedApps = runCatching {
            packageManager.getInstalledApplications(0)
            .asSequence()
            .mapNotNull { applicationInfo -> applicationInfo.toInstalledApp() }
            .distinctBy(InstalledApp::packageName)
            .sortedBy { installedApp -> installedApp.appName.lowercase() }
            .toList()
        }.getOrElse { error ->
            Log.e(TAG, "Unable to load installed applications.", error)
            emptyList()
        }
        synchronized(CACHE_LOCK) {
            loadedApps.forEach {
                appCache[it.packageName] = it
                unavailablePackages.remove(it.packageName)
            }
            cachedInstalledApps = loadedApps
            installedAppsCachedAtMillis = now
        }
        Log.d(TAG, "PackageManager batch=${SystemClock.elapsedRealtime() - startedAt}ms apps=${loadedApps.size}")
        return loadedApps
    }

    private fun ApplicationInfo.toInstalledApp(): InstalledApp? {
        if (packageName.startsWith(ANDROID_PACKAGE_PREFIX)) {
            return null
        }

        if (flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            return null
        }

        val appName = runCatching {
            loadLabel(packageManager).toString()
        }.getOrElse { error ->
            Log.w(TAG, "Unable to resolve application label for $packageName.", error)
            packageName
        }.ifBlank { packageName }

        val appIcon = runCatching {
            loadIcon(packageManager).toBitmap(
                width = APP_ICON_SIZE_PX,
                height = APP_ICON_SIZE_PX,
            )
        }.getOrElse { error ->
            Log.w(TAG, "Unable to resolve application icon for $packageName.", error)
            null
        }

        return InstalledApp(
            packageName = packageName,
            appName = appName,
            appIcon = appIcon,
        )
    }

    private companion object {
        const val TAG = "InstalledAppInfoProvider"
        const val ANDROID_PACKAGE_PREFIX = "com.android."
        const val APP_ICON_SIZE_PX = 144
        const val INSTALLED_APPS_CACHE_MILLIS = 5 * 60 * 1_000L
        val CACHE_LOCK = Any()
        val appCache = mutableMapOf<String, InstalledApp>()
        val unavailablePackages = mutableSetOf<String>()
        var cachedInstalledApps: List<InstalledApp>? = null
        var installedAppsCachedAtMillis = 0L
    }
}
