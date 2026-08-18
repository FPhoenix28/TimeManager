package com.example.timemanager.data.repository

import com.example.timemanager.data.model.InstalledApp

/** Provides the list of installed user applications. */
class InstalledAppRepository(
    private val installedAppInfoProvider: InstalledAppInfoProvider,
) {
    fun getUserInstalledApps(): List<InstalledApp> =
        installedAppInfoProvider.getUserInstalledApps()
}
