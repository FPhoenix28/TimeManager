package com.example.timemanager.config

import com.example.timemanager.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class ReleaseTimeConfigTest {
    @Test fun releaseProviderIsAlwaysProduction() {
        assertFalse(BuildConfig.DEBUG)
        assertEquals("com.fphoenix28.timemanager", BuildConfig.APPLICATION_ID)
        assertEquals(TimeConfig.production(), BuildTimeConfig.value)
        repeat(3) { assertEquals(TimeConfig.production(), BuildTimeConfig.provider.current()) }
        assertNull(BuildTimeConfig.value.sessionInterventionOverrideSeconds)
        val c = BuildTimeConfig.provider.current()
        assertFalse(c.debugTimeOverridesEnabled)
        assertEquals(listOf(900L, 1800L, 900L, 5L, 300L, 3L, 480L, 480L, 300L),
            listOf(c.firstSessionReminderSeconds, c.secondSessionReminderSeconds, c.extensionDurationSeconds,
                c.frictionGateSeconds, c.frictionResetIdleSeconds, c.frictionBypassSeconds,
                c.sessionFirstIncrementSeconds, c.sessionSecondIncrementSeconds, c.sessionRepeatingIncrementSeconds))
    }
}
