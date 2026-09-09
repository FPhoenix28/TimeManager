package com.example.timemanager.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSettingsRepositoryTest {
    @Test
    fun appsWithoutStoredSettingUseTenMinuteDefault() {
        assertEquals(10, SessionSettingsRepository.DEFAULT_REMINDER_MINUTES)
    }
}
