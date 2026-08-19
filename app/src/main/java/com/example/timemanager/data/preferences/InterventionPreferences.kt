package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timemanager.data.model.InterventionState

private val Context.interventionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "limit_interventions",
)

/** Stores grace periods and daily extension counts by package. */
class InterventionPreferences(context: Context) {
    private val dataStore = context.applicationContext.interventionDataStore

    suspend fun getState(packageName: String, usageDayKey: String): InterventionState {
        val preferences = dataStore.edit { values ->
            resetForNewUsageDay(values, packageName, usageDayKey)
        }
        return preferences.toState(packageName, usageDayKey)
    }

    suspend fun grantGracePeriod(
        packageName: String,
        usageDayKey: String,
        gracePeriodEndTime: Long,
        maxExtensions: Int,
    ): InterventionState? {
        var granted = false
        val preferences = dataStore.edit { values ->
            resetForNewUsageDay(values, packageName, usageDayKey)
            val countKey = extensionCountKey(packageName)
            val count = values[countKey] ?: 0
            if (count < maxExtensions) {
                values[countKey] = count + 1
                values[graceEndKey(packageName)] = gracePeriodEndTime
                granted = true
            }
        }
        return if (granted) preferences.toState(packageName, usageDayKey) else null
    }

    suspend fun clearGracePeriod(
        packageName: String,
        usageDayKey: String,
    ): InterventionState {
        val preferences = dataStore.edit { values ->
            resetForNewUsageDay(values, packageName, usageDayKey)
            values[graceEndKey(packageName)] = 0L
        }
        return preferences.toState(packageName, usageDayKey)
    }

    suspend fun remove(packageName: String) {
        dataStore.edit { values ->
            values.remove(usageDayKey(packageName))
            values.remove(graceEndKey(packageName))
            values.remove(extensionCountKey(packageName))
        }
    }

    private fun resetForNewUsageDay(
        values: androidx.datastore.preferences.core.MutablePreferences,
        packageName: String,
        usageDayKey: String,
    ) {
        val dayKey = usageDayKey(packageName)
        if (values[dayKey] != usageDayKey) {
            values[dayKey] = usageDayKey
            values[graceEndKey(packageName)] = 0L
            values[extensionCountKey(packageName)] = 0
        }
    }

    private fun Preferences.toState(
        packageName: String,
        usageDayKey: String,
    ) = InterventionState(
        packageName = packageName,
        usageDayKey = usageDayKey,
        gracePeriodEndTime = this[graceEndKey(packageName)] ?: 0L,
        extensionsUsed = this[extensionCountKey(packageName)] ?: 0,
    )

    private fun usageDayKey(packageName: String) =
        stringPreferencesKey("usage_day:$packageName")

    private fun graceEndKey(packageName: String) =
        longPreferencesKey("grace_end:$packageName")

    private fun extensionCountKey(packageName: String) =
        intPreferencesKey("extension_count:$packageName")
}
