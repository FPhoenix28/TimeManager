package com.example.timemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.focusModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "focus_mode",
)

class FocusModePreferences(context: Context) {
    private val dataStore = context.applicationContext.focusModeDataStore

    fun observeState(): Flow<FocusModeState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toFocusModeState() }

    suspend fun save(state: FocusModeState) {
        dataStore.edit { preferences ->
            preferences.writeState(state)
        }
    }

    suspend fun updateState(transform: (FocusModeState) -> FocusModeState) {
        dataStore.edit { preferences ->
            preferences.writeState(transform(preferences.toFocusModeState()))
        }
    }

    private fun Preferences.toFocusModeState(): FocusModeState {
        val mode = FocusModeType.entries.firstOrNull { it.name == this[MODE] }
            ?: FocusModeType.OFF
        if (mode == FocusModeType.OFF) return FocusModeState()

        val state = FocusModeState(
            mode = mode,
            status = FocusModeStatus.entries.firstOrNull { it.name == this[STATUS] }
                ?: FocusModeStatus.ACTIVE,
            startTimeMillis = this[START] ?: 0L,
            endTimeMillis = this[END] ?: 0L,
            blockedPackages = this[BLOCKED].orEmpty(),
        )
        return if (state.hasValidTimeWindow) state else FocusModeState()
    }

    private fun MutablePreferences.writeState(state: FocusModeState) {
        this[MODE] = state.mode.name
        this[STATUS] = state.status.name
        this[START] = state.startTimeMillis
        this[END] = state.endTimeMillis
        this[BLOCKED] = state.blockedPackages
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val STATUS = stringPreferencesKey("status")
        val START = longPreferencesKey("start_time_millis")
        val END = longPreferencesKey("end_time_millis")
        val BLOCKED = stringSetPreferencesKey("blocked_packages")
    }
}
