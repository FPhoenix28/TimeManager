package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.EffectiveAppLimit
import com.example.timemanager.domain.profile.*
import org.junit.Assert.*
import org.junit.Test

class PolicySecondsPrecisionTest {
    @Test fun secondsReachRulesProjectionWithoutMinuteTruncationOrPolicyMismatch() {
        val fixture = ShadowFixtures()
        fixture.policy = LegacyPolicyInput(true, true, null, null,
            dailyLimitSeconds = 60L, storedSessionReminderSeconds = 20L)
        val limit = EffectiveAppLimit(fixture.pkg, 60L, 0L)
        val observation = fixture.shadow.capture(fixture.pkg, 20L, 20L, limit,
            com.example.timemanager.domain.limit.UsageLimitEvaluator().evaluateSeconds(fixture.pkg, 20L, 60L),
            fixture.friction.snapshot(fixture.pkg), false)
        val policy = observation.context!!.effectivePolicy.appPolicy
        assertEquals(DailyRuleConfig.Explicit(60L), policy.daily)
        assertEquals(FirstSessionReminder.Explicit(20L), policy.session.firstReminder)
        assertFalse(ShadowFact.POLICY_INPUT_CHANGED in observation.missing)
    }
}
