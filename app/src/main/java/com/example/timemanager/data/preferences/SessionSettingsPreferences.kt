package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session_settings",
)

/** Persists an independent continuous-use reminder threshold for each package. */
class SessionSettingsPreferences(context: Context) {
    private val dataStore = context.applicationContext.sessionSettingsDataStore
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
        dataStore.edit { preferences -> preferences[reminderKey(packageName)] = minutes }
    }

    suspend fun remove(packageName: String) {
        dataStore.edit { preferences -> preferences.remove(reminderKey(packageName)) }
    }

    private fun reminderKey(packageName: String): Preferences.Key<Int> {
        require(packageName.isNotBlank())
        return intPreferencesKey("$KEY_PREFIX$packageName")
    }

    private companion object {
        const val KEY_PREFIX = "session_reminder_minutes:"
    }
}
