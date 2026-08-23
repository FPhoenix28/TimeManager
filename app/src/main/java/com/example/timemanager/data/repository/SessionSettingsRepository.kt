package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppSessionSettings
import com.example.timemanager.data.preferences.SessionSettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionSettingsRepository(
    private val preferences: SessionSettingsPreferences,
) {
    fun observe(packageName: String): Flow<AppSessionSettings> =
        preferences.observeReminderMinutes(packageName).map { storedMinutes ->
            AppSessionSettings(
                packageName = packageName,
                reminderMinutes = storedMinutes ?: DEFAULT_REMINDER_MINUTES,
            )
        }

    fun observeStoredSettings(): Flow<Map<String, Int>> =
        preferences.observeAllReminderMinutes()

    suspend fun save(packageName: String, reminderMinutes: Int) {
        require(reminderMinutes in MIN_REMINDER_MINUTES..MAX_REMINDER_MINUTES)
        preferences.setReminderMinutes(packageName, reminderMinutes)
    }

    suspend fun remove(packageName: String) = preferences.remove(packageName)

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 30
        const val MIN_REMINDER_MINUTES = 5
        const val MAX_REMINDER_MINUTES = 180
    }
}
