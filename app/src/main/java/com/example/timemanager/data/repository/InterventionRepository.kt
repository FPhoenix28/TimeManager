package com.example.timemanager.data.repository

import com.example.timemanager.data.model.InterventionState
import com.example.timemanager.data.preferences.InterventionPreferences

class InterventionRepository(
    private val preferences: InterventionPreferences,
) {
    suspend fun getState(packageName: String, usageDayKey: String): InterventionState =
        preferences.getState(packageName, usageDayKey)

    suspend fun grantGracePeriod(
        packageName: String,
        usageDayKey: String,
        gracePeriodEndTime: Long,
        maxExtensions: Int,
    ): InterventionState? = preferences.grantGracePeriod(
        packageName,
        usageDayKey,
        gracePeriodEndTime,
        maxExtensions,
    )

    suspend fun clearGracePeriod(
        packageName: String,
        usageDayKey: String,
    ): InterventionState = preferences.clearGracePeriod(packageName, usageDayKey)

    suspend fun remove(packageName: String) = preferences.remove(packageName)
}
