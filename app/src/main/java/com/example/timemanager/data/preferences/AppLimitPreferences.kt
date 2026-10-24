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

private val Context.appLimitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_limits",
)

/** Stores the daily usage limit associated with each application package. */
class AppLimitPreferences(
    private val dataStore: DataStore<Preferences>,
    private val allowSeconds: Boolean = BuildTimeConfig.supportsPreciseAppDurations,
) {
    constructor(context: Context) : this(context.applicationContext.appLimitDataStore)

    fun observeLimitSeconds(packageName: String): Flow<Long?> = safePreferences.map {
        if (allowSeconds) it[secondsKey(packageName)] ?: it[limitKey(packageName)]?.times(60L)
        else it[limitKey(packageName)]?.times(60L)
    }

    fun observeAllLimitSeconds(): Flow<Map<String, Long>> = safePreferences.map { preferences ->
        val limits = preferences.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(LIMIT_KEY_PREFIX) && value is Int)
                key.name.removePrefix(LIMIT_KEY_PREFIX) to value * 60L else null
        }.toMap().toMutableMap()
        if (allowSeconds) preferences.asMap().forEach { (key, value) ->
            if (key.name.startsWith(SECONDS_KEY_PREFIX) && value is Long)
                limits[key.name.removePrefix(SECONDS_KEY_PREFIX)] = value
        }
        limits
    }

    suspend fun setLimitSeconds(packageName: String, seconds: Long) {
        check(allowSeconds)
        require(seconds in 1L..359_999L)
        dataStore.edit { it[secondsKey(packageName)] = seconds }
    }

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
            preferences.remove(secondsKey(packageName))
        }
    }

    suspend fun removeLimit(packageName: String) {
        val key = limitKey(packageName)
        dataStore.edit { preferences ->
            preferences.remove(key)
            preferences.remove(secondsKey(packageName))
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

    private fun secondsKey(packageName: String): Preferences.Key<Long> {
        require(packageName.isNotBlank())
        return longPreferencesKey("$SECONDS_KEY_PREFIX$packageName")
    }

    private companion object {
        const val LIMIT_KEY_PREFIX = "daily_limit_minutes:"
        const val SECONDS_KEY_PREFIX = "daily_limit_seconds:"
    }
}
