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
        assertEquals(5L, BuildTimeConfig.value.frictionGateSeconds)
        assertEquals(300L, BuildTimeConfig.value.frictionResetIdleSeconds)
        assertEquals(3L, BuildTimeConfig.value.frictionBypassSeconds)
        org.junit.Assert.assertNull(BuildTimeConfig.value.sessionInterventionOverrideSeconds)
    }

    @Test
    fun fastRegressionRequiresExplicitInjection() {
        assertTrue(BuildTimeConfig.fastRegression.debugTimeOverridesEnabled)
        assertEquals(15L, BuildTimeConfig.fastRegression.firstSessionReminderSeconds)
        assertEquals(30L, BuildTimeConfig.fastRegression.secondSessionReminderSeconds)
        assertEquals(30L, BuildTimeConfig.fastRegression.extensionDurationSeconds)
        assertEquals(20L, BuildTimeConfig.fastRegression.sessionInterventionOverrideSeconds)
        assertEquals(30L, BuildTimeConfig.fastRegression.frictionResetIdleSeconds)
        assertEquals(5L, BuildTimeConfig.fastRegression.frictionGateSeconds)
    }
}
