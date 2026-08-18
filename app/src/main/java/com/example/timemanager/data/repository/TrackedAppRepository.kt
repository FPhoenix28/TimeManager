package com.example.timemanager.data.repository

import com.example.timemanager.data.preferences.TrackedAppPreferences
import kotlinx.coroutines.flow.Flow

/** Provides and updates the package-name set selected for real-time tracking. */
class TrackedAppRepository(
    private val preferences: TrackedAppPreferences,
) {
    fun observeTrackedPackageNames(): Flow<Set<String>> =
        preferences.observeTrackedPackageNames()

    suspend fun setTracked(packageName: String, isTracked: Boolean) {
        preferences.setTracked(packageName, isTracked)
    }
}
