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

private val Context.appLimitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_limits",
)

/** Stores the daily usage limit associated with each application package. */
class AppLimitPreferences(context: Context) {
    private val dataStore = context.applicationContext.appLimitDataStore

    fun observeLimitMinutes(packageName: String): Flow<Int?> {
        val key = limitKey(packageName)
        return safePreferences.map { preferences -> preferences[key] }
    }

    fun observeAllLimits(): Flow<Map<String, Int>> = safePreferences.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) ->
            val packageName = key.name.removePrefix(LIMIT_KEY_PREFIX)
            if (
                key.name.startsWith(LIMIT_KEY_PREFIX) &&
                packageName.isNotBlank() &&
                value is Int
            ) {
                packageName to value
            } else {
                null
            }
        }.toMap()
    }

    suspend fun setLimitMinutes(packageName: String, limitMinutes: Int) {
        require(limitMinutes >= 0) { "limitMinutes must not be negative." }
        val key = limitKey(packageName)
        dataStore.edit { preferences ->
            preferences[key] = limitMinutes
        }
    }

    suspend fun removeLimit(packageName: String) {
        val key = limitKey(packageName)
        dataStore.edit { preferences ->
            preferences.remove(key)
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

    private fun limitKey(packageName: String): Preferences.Key<Int> {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        return intPreferencesKey("$LIMIT_KEY_PREFIX$packageName")
    }

    private companion object {
        const val LIMIT_KEY_PREFIX = "daily_limit_minutes:"
    }
}
