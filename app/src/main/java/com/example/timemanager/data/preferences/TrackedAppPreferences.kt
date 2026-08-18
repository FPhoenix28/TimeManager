package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.trackedAppDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tracked_apps",
)

/** Persists the package names selected for real-time tracking. */
class TrackedAppPreferences(context: Context) {
    private val dataStore = context.applicationContext.trackedAppDataStore

    fun observeTrackedPackageNames(): Flow<Set<String>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[TRACKED_PACKAGES].orEmpty() }

    suspend fun setTracked(packageName: String, isTracked: Boolean) {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        dataStore.edit { preferences ->
            val packages = preferences[TRACKED_PACKAGES].orEmpty().toMutableSet()
            if (isTracked) packages += packageName else packages -= packageName
            preferences[TRACKED_PACKAGES] = packages
        }
    }

    private companion object {
        val TRACKED_PACKAGES = stringSetPreferencesKey("tracked_package_names")
    }
}
