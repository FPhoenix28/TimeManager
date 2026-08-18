package com.example.timemanager.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.example.timemanager.data.model.InstalledApp

/** Resolves display information for user-installed applications. */
class InstalledAppInfoProvider(
    private val packageManager: PackageManager,
) {
    fun getUserInstalledApp(packageName: String): InstalledApp? {
        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse { error ->
            Log.w(
                TAG,
                "Skipping $packageName because its application info is unavailable or not visible.",
                error,
            )
            return null
        }

        return applicationInfo.toInstalledApp()
    }

    fun getUserInstalledApps(): List<InstalledApp> = runCatching {
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

    private fun ApplicationInfo.toInstalledApp(): InstalledApp? {
        if (packageName.startsWith(ANDROID_PACKAGE_PREFIX)) {
            return null
        }

        if (flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            Log.d(TAG, "Skipping system application: $packageName")
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
    }
}
