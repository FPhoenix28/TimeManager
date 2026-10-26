package com.example.timemanager.domain.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileConfigurationTest {
    @Test fun defaultProfileIdentityIsStableAndContainsOnlyConfiguration() {
        val policy = AppPolicy("example.app", managed = true)
        val profile = ProfileDefinition(ProfileId.DEFAULT, "Default Profile", true, listOf(policy))
        assertEquals(ProfileId("default"), profile.id)
        assertEquals(listOf(policy), profile.appPolicies)
        assertEquals(1, profile.configVersion)
    }

    @Test fun dailyUnsetExplicitZeroAndExplicitPositiveRemainDistinct() {
        val values = listOf(DailyRuleConfig.Unset, DailyRuleConfig.Explicit(0L), DailyRuleConfig.Explicit(1_800L))
        assertEquals(3, values.distinct().size)
        assertEquals(0L, (values[1] as DailyRuleConfig.Explicit).baseLimitSeconds)
        assertEquals(1_800L, (values[2] as DailyRuleConfig.Explicit).baseLimitSeconds)
    }

    @Test fun absentDailyConfigurationDoesNotSynthesizeAnyQuota() {
        assertSame(DailyRuleConfig.Unset, AppPolicy("example.app", managed = true).daily)
    }

    @Test fun inheritedTenAndExplicitTenHaveEqualThresholdsButDifferentProvenance() {
        val inherited = SessionRuleConfig()
        val explicit = SessionRuleConfig(FirstSessionReminder.Explicit(600L))
        assertEquals(600L, inherited.effectiveFirstReminderSeconds)
        assertEquals(600L, explicit.effectiveFirstReminderSeconds)
        assertNotEquals(inherited.firstReminder, explicit.firstReminder)
        assertNotEquals(inherited, explicit)
    }

    @Test fun explicitFifteenAndTwentyMinuteValuesAreNotOverwrittenByDefault() {
        for (seconds in listOf(600L, 900L, 1_200L)) {
            val setting = FirstSessionReminder.Explicit(seconds)
            val config = SessionRuleConfig(setting)
            assertSame(setting, config.firstReminder)
            assertEquals(seconds, config.effectiveFirstReminderSeconds)
        }
    }

    @Test fun profileOwnsACopiedFrozenPolicyCollection() {
        val policy = AppPolicy("example.app", managed = true)
        val input = mutableListOf(policy)
        val profile = ProfileDefinition(ProfileId.DEFAULT, "Default", true, input)
        input.clear()
        assertEquals(listOf(policy), profile.appPolicies)
        assertThrows(UnsupportedOperationException::class.java) {
            (profile.appPolicies as MutableList<AppPolicy>).clear()
        }
    }

    @Test fun invalidConfigurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { DailyRuleConfig.Explicit(-1L) }
        assertThrows(IllegalArgumentException::class.java) { FirstSessionReminder.Explicit(0L) }
        val policy = AppPolicy("example.app", managed = true)
        assertThrows(IllegalArgumentException::class.java) {
            ProfileDefinition(ProfileId.DEFAULT, "Default", true, listOf(policy, policy))
        }
    }
}
