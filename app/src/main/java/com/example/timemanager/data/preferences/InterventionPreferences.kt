package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timemanager.data.model.InterventionState
import kotlinx.coroutines.flow.first

private val Context.interventionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "limit_interventions",
)

/** Stores temporary allowance extensions for one 04:00-based usage day. */
class InterventionPreferences(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.interventionDataStore)

    suspend fun getState(packageName: String, usageDayKey: String): InterventionState {
        val current = dataStore.data.first()
        if (
            current[usageDayKey(packageName)] == usageDayKey &&
            current[extensionSecondsKey(packageName)] != null
        ) {
            return current.toState(packageName, usageDayKey)
        }
        val preferences = dataStore.edit { values ->
            resetForNewUsageDay(values, packageName, usageDayKey)
        }
        return preferences.toState(packageName, usageDayKey)
    }

    suspend fun grantExtension(
        packageName: String,
        usageDayKey: String,
        extensionSeconds: Long,
    ): InterventionState {
        val preferences = dataStore.edit { values ->
            resetForNewUsageDay(values, packageName, usageDayKey)
            val extensionKey = extensionSecondsKey(packageName)
            values[extensionKey] = (values[extensionKey] ?: 0L) + extensionSeconds
            values[graceEndKey(packageName)] = 0L
        }
        return preferences.toState(packageName, usageDayKey)
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
            values.remove(extensionSecondsKey(packageName))
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
            values[extensionSecondsKey(packageName)] = 0L
        } else if (values[extensionSecondsKey(packageName)] == null) {
            values[extensionSecondsKey(packageName)] = 0L
        }
    }

    private fun Preferences.toState(
        packageName: String,
        usageDayKey: String,
    ) = InterventionState(
        packageName = packageName,
        usageDayKey = usageDayKey,
        gracePeriodEndTime = this[graceEndKey(packageName)] ?: 0L,
        todayExtensionSeconds = this[extensionSecondsKey(packageName)] ?: 0L,
    )

    private fun usageDayKey(packageName: String) =
        stringPreferencesKey("usage_day:$packageName")

    private fun graceEndKey(packageName: String) =
        longPreferencesKey("grace_end:$packageName")

    private fun extensionSecondsKey(packageName: String) =
        longPreferencesKey("extension_seconds:$packageName")
}
