package com.example.timemanager.data.repository

import com.example.timemanager.data.model.InterventionState
import com.example.timemanager.data.preferences.InterventionPreferences

class InterventionRepository(
    private val preferences: InterventionPreferences,
) {
    suspend fun getState(packageName: String, usageDayKey: String): InterventionState =
        preferences.getState(packageName, usageDayKey)

    suspend fun grantExtension(
        packageName: String,
        usageDayKey: String,
        extensionSeconds: Long,
    ): InterventionState = preferences.grantExtension(
        packageName,
        usageDayKey,
        extensionSeconds,
    )

    suspend fun clearGracePeriod(
        packageName: String,
        usageDayKey: String,
    ): InterventionState = preferences.clearGracePeriod(packageName, usageDayKey)

    suspend fun remove(packageName: String) = preferences.remove(packageName)
}
