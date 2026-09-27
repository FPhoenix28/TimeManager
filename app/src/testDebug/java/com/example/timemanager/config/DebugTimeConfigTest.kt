package com.example.timemanager.config

import com.example.timemanager.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTimeConfigTest {
    @Test
    fun debugBuildDefaultsToProductionDurations() {
        assertTrue(BuildConfig.DEBUG)
        assertFalse(BuildTimeConfig.value.debugTimeOverridesEnabled)
        assertEquals(15L * 60L, BuildTimeConfig.value.firstSessionReminderSeconds)
        assertEquals(30L * 60L, BuildTimeConfig.value.secondSessionReminderSeconds)
        assertEquals(15L * 60L, BuildTimeConfig.value.extensionDurationSeconds)
    }

    @Test
    fun fastRegressionRequiresExplicitInjection() {
        assertTrue(BuildTimeConfig.fastRegression.debugTimeOverridesEnabled)
        assertEquals(15L, BuildTimeConfig.fastRegression.firstSessionReminderSeconds)
        assertEquals(30L, BuildTimeConfig.fastRegression.secondSessionReminderSeconds)
        assertEquals(30L, BuildTimeConfig.fastRegression.extensionDurationSeconds)
    }
}
