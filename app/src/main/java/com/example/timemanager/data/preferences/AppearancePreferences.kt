package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timemanager.ui.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(
    name = "appearance",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

internal class AppearancePreferences(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.appearanceDataStore)

    val themeMode = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            val storedMode = preferences[THEME_MODE]
            ThemeMode.entries.firstOrNull { it.name == storedMode } ?: ThemeMode.SYSTEM
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
