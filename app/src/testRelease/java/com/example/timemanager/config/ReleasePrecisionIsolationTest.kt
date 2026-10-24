package com.example.timemanager.config

import androidx.datastore.preferences.core.*
import com.example.timemanager.data.preferences.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class ReleasePrecisionIsolationTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @After fun close() { scope.cancel() }

    @Test fun releaseIgnoresDevSecondsAndPreservesMinuteKeys() = runBlocking {
        val data = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("release.preferences_pb") }
        data.edit {
            it[intPreferencesKey("daily_limit_minutes:a")] = 30
            it[longPreferencesKey("daily_limit_seconds:a")] = 20L
            it[intPreferencesKey("session_reminder_minutes:a")] = 15
            it[longPreferencesKey("session_reminder_seconds:a")] = 20L
        }
        assertFalse(BuildTimeConfig.supportsPreciseAppDurations)
        assertEquals(1800L, AppLimitPreferences(data).observeLimitSeconds("a").first())
        assertEquals(900L, SessionSettingsPreferences(data).observeReminderSeconds("a").first())
        assertTrue(runCatching { AppLimitPreferences(data).setLimitSeconds("a", 20L) }.isFailure)
    }
}
