package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.notificationStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_state",
)

/** Persists the last date each package received a threshold notification. */
class NotificationStatePreferences(context: Context) {
    private val dataStore = context.applicationContext.notificationStateDataStore

    fun observeLastSentDate(packageName: String, thresholdPercent: Int): Flow<String?> {
        val key = notificationKey(packageName, thresholdPercent)
        return safePreferences.map { preferences -> preferences[key] }
    }

    suspend fun setLastSentDate(
        packageName: String,
        thresholdPercent: Int,
        date: String,
    ) {
        require(date.isNotBlank()) { "date must not be blank." }
        val key = notificationKey(packageName, thresholdPercent)
        dataStore.edit { preferences ->
            preferences[key] = date
        }
    }

    suspend fun remove(packageName: String) {
        dataStore.edit { preferences ->
            SUPPORTED_THRESHOLDS.forEach { threshold ->
                preferences.remove(notificationKey(packageName, threshold))
            }
        }
    }

    private val safePreferences: Flow<Preferences>
        get() = dataStore.data.catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    private fun notificationKey(
        packageName: String,
        thresholdPercent: Int,
    ): Preferences.Key<String> {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        require(thresholdPercent in SUPPORTED_THRESHOLDS) {
            "Unsupported notification threshold: $thresholdPercent"
        }
        return stringPreferencesKey("last_sent_${thresholdPercent}:$packageName")
    }

    private companion object {
        val SUPPORTED_THRESHOLDS = setOf(80, 100)
    }
}
