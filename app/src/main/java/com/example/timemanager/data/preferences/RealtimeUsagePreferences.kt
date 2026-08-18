package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timemanager.data.model.RealtimeUsageRecord
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.realtimeUsageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "realtime_usage",
)

/** Stores the current day's real-time usage estimates by package name. */
class RealtimeUsagePreferences(context: Context) {
    private val dataStore = context.applicationContext.realtimeUsageDataStore

    suspend fun getRecordsForDate(date: String): Map<String, Long> {
        resetIfDateChanged(date)
        return safePreferences.first().asMap().mapNotNull { (key, value) ->
            val packageName = key.name.removePrefix(SECONDS_KEY_PREFIX)
            if (
                key.name.startsWith(SECONDS_KEY_PREFIX) &&
                packageName.isNotBlank() &&
                value is Long
            ) {
                packageName to value
            } else {
                null
            }
        }.toMap()
    }

    suspend fun save(record: RealtimeUsageRecord) {
        require(record.packageName.isNotBlank()) { "packageName must not be blank." }
        require(record.seconds >= 0L) { "seconds must not be negative." }

        dataStore.edit { preferences ->
            if (preferences[DATE_KEY] != record.date) {
                preferences.clear()
                preferences[DATE_KEY] = record.date
            }
            preferences[secondsKey(record.packageName)] = record.seconds
        }
    }

    private suspend fun resetIfDateChanged(date: String) {
        dataStore.edit { preferences ->
            if (preferences[DATE_KEY] != date) {
                preferences.clear()
                preferences[DATE_KEY] = date
            }
        }
    }

    private val safePreferences: Flow<Preferences>
        get() = dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    private fun secondsKey(packageName: String): Preferences.Key<Long> =
        longPreferencesKey("$SECONDS_KEY_PREFIX$packageName")

    private companion object {
        val DATE_KEY = stringPreferencesKey("record_date")
        const val SECONDS_KEY_PREFIX = "realtime_seconds:"
    }
}
