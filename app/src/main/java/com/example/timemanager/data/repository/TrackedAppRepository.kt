package com.example.timemanager.data.repository

import com.example.timemanager.data.preferences.TrackedAppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Provides and updates the package-name set selected for real-time tracking. */
class TrackedAppRepository(
    private val preferences: TrackedAppPreferences,
) {
    fun observeTrackedPackageNames(): Flow<Set<String>> =
        preferences.observeTrackedPackageNames()

    suspend fun setTracked(packageName: String, isTracked: Boolean) {
        preferences.setTracked(packageName, isTracked)
    }

    suspend fun isTracked(packageName: String): Boolean =
        packageName in observeTrackedPackageNames().first()
}
