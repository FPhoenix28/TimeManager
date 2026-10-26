package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.timemanager.config.BuildTimeConfig
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session_settings",
)

/** Persists an independent continuous-use reminder threshold for each package. */
class SessionSettingsPreferences(
    private val dataStore: DataStore<Preferences>,
    private val allowSeconds: Boolean = BuildTimeConfig.supportsPreciseAppDurations,
) {
    constructor(context: Context) : this(context.applicationContext.sessionSettingsDataStore)

    fun observeReminderSeconds(packageName: String): Flow<Long?> = safePreferences.map {
        if (allowSeconds) it[secondsKey(packageName)] ?: it[reminderKey(packageName)]?.times(60L)
        else it[reminderKey(packageName)]?.times(60L)
    }

    fun observeAllReminderSeconds(): Flow<Map<String, Long>> = safePreferences.map { preferences ->
        val settings = preferences.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(KEY_PREFIX) && value is Int)
                key.name.removePrefix(KEY_PREFIX) to value * 60L else null
        }.toMap().toMutableMap()
        if (allowSeconds) preferences.asMap().forEach { (key, value) ->
            if (key.name.startsWith(SECONDS_KEY_PREFIX) && value is Long)
                settings[key.name.removePrefix(SECONDS_KEY_PREFIX)] = value
        }
        settings
    }

    suspend fun setReminderSeconds(packageName: String, seconds: Long) {
        check(allowSeconds)
        require(seconds in 1L..359_999L)
        dataStore.edit { it[secondsKey(packageName)] = seconds }
    }
    private val safePreferences: Flow<Preferences>
        get() = dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    fun observeReminderMinutes(packageName: String): Flow<Int?> {
        val key = reminderKey(packageName)
        return safePreferences.map { preferences -> preferences[key] }
    }

    fun observeAllReminderMinutes(): Flow<Map<String, Int>> = safePreferences.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) ->
            val packageName = key.name.removePrefix(KEY_PREFIX)
            if (key.name.startsWith(KEY_PREFIX) && packageName.isNotBlank() && value is Int) {
                packageName to value
            } else {
                null
            }
        }.toMap()
    }

    suspend fun setReminderMinutes(packageName: String, minutes: Int) {
        dataStore.edit { preferences ->
            preferences[reminderKey(packageName)] = minutes
            preferences.remove(secondsKey(packageName))
        }
    }

    suspend fun remove(packageName: String) {
        dataStore.edit { preferences ->
            preferences.remove(reminderKey(packageName))
            preferences.remove(secondsKey(packageName))
        }
    }

    private fun reminderKey(packageName: String): Preferences.Key<Int> {
        require(packageName.isNotBlank())
        return intPreferencesKey("$KEY_PREFIX$packageName")
    }

    private fun secondsKey(packageName: String): Preferences.Key<Long> {
        require(packageName.isNotBlank())
        return longPreferencesKey("$SECONDS_KEY_PREFIX$packageName")
    }

    private companion object {
        const val KEY_PREFIX = "session_reminder_minutes:"
        const val SECONDS_KEY_PREFIX = "session_reminder_seconds:"
    }
}
