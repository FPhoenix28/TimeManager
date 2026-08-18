package com.example.timemanager.data.repository

import com.example.timemanager.data.preferences.AppLimitPreferences
import kotlinx.coroutines.flow.Flow

/** Exposes saved per-app daily limits without leaking DataStore details. */
class AppLimitRepository(
    private val preferences: AppLimitPreferences,
) {
    fun observeLimitMinutes(packageName: String): Flow<Int?> =
        preferences.observeLimitMinutes(packageName)

    fun observeAllLimits(): Flow<Map<String, Int>> = preferences.observeAllLimits()

    suspend fun setLimitMinutes(packageName: String, limitMinutes: Int) {
        preferences.setLimitMinutes(packageName, limitMinutes)
    }

    suspend fun removeLimit(packageName: String) {
        preferences.removeLimit(packageName)
    }
}
