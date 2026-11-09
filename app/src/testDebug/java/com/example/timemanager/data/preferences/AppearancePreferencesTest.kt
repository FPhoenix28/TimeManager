package com.example.timemanager.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.timemanager.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearancePreferencesTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun missingAndUnrecognizedValuesFollowSystem() = runBlocking {
        val file = directory.root.resolve("appearance.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            val preferences = AppearancePreferences(store)
            assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
            store.edit { it[stringPreferencesKey("theme_mode")] = "OLD_VALUE" }
            assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun selectionIsRestoredFromDiskInANewStore() = runBlocking {
        val file = directory.root.resolve("appearance.preferences_pb")
        val original = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + original)) { file }
            AppearancePreferences(store).setThemeMode(ThemeMode.DARK)
        } finally {
            original.cancelAndJoin()
        }
        val restarted = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + restarted)) { file }
            assertEquals(ThemeMode.DARK, AppearancePreferences(store).themeMode.first())
        } finally {
            restarted.cancelAndJoin()
        }
    }
}
