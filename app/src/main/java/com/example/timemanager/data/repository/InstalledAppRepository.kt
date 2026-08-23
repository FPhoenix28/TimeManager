package com.example.timemanager.data.repository

import com.example.timemanager.data.model.InstalledApp

/** Provides the list of installed user applications. */
class InstalledAppRepository(
    private val installedAppInfoProvider: InstalledAppInfoProvider,
) {
    fun getUserInstalledApp(packageName: String): InstalledApp? =
        installedAppInfoProvider.getUserInstalledApp(packageName)

    fun getUserInstalledApps(): List<InstalledApp> =
        installedAppInfoProvider.getUserInstalledApps()
}
