package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.profile.*
import com.example.timemanager.domain.rules.*
import org.junit.Assert.*
import org.junit.Test

class LegacyRulesSnapshotAdapterTest {
    @Test fun trackedDailyAndStoredSessionProjectToEffectiveLegacyPolicy() {
        val f = ShadowFixtures()
        f.policy = f.policy.copy(storedSessionReminderMinutes = 20)
        val context = f.capture().context!!
        assertEquals(ProfileId.DEFAULT, context.effectivePolicy.sourceProfileId)
        assertTrue(context.effectivePolicy.appPolicy.managed)
        assertEquals(DailyRuleConfig.Explicit(900L), context.effectivePolicy.appPolicy.daily)
        assertEquals(FirstSessionReminder.Explicit(1_200L), context.effectivePolicy.appPolicy.session.firstReminder)
        assertEquals(TargetDisposition.UNCLASSIFIED, context.target.disposition)
        assertEquals(FocusRuntimeSnapshot.Off, context.focus) // Deliberately scoped, not a real Focus observation.
    }

    @Test fun absentQuotaIsUnsetAndAbsentSessionSettingInheritsDefault() {
        val f = ShadowFixtures()
        f.policy = f.policy.copy(dailyLimitMinutes = null)
        val context = f.capture().context!!
        assertEquals(DailyRuleConfig.Unset, context.effectivePolicy.appPolicy.daily)
        assertEquals(FirstSessionReminder.InheritedDefault, context.effectivePolicy.appPolicy.session.firstReminder)
        assertNull(context.daily)
    }

    @Test fun explicitZeroAndExplicitDefaultValueAreNotTreatedAsAbsent() {
        val f = ShadowFixtures()
        f.policy = f.policy.copy(dailyLimitMinutes = 0, storedSessionReminderMinutes = 10)
        val policy = f.capture().context!!.effectivePolicy.appPolicy
        assertEquals(DailyRuleConfig.Explicit(0L), policy.daily)
        assertEquals(FirstSessionReminder.Explicit(600L), policy.session.firstReminder)
    }

    @Test fun usageExtensionAndUsageDayComeFromSuppliedLegacyFacts() {
        val f = ShadowFixtures()
        f.extension = 900L
        val daily = f.capture().context!!.daily!!
        assertEquals(UsageDayRuntimeIdentity(f.pkg, UsageDayKey(f.day)), daily.identity)
        assertEquals(1_000L, daily.usageSeconds)
        assertEquals(900L, daily.todayExtensionSeconds)
    }

    @Test fun episodePendingGateAndAdmissionAreCopiedWithoutTransition() {
        val f = ShadowFixtures()
        val gate = f.friction.beginGate(f.pkg, SessionGatePhase.REMINDER)!!
        val before = f.friction.snapshot(f.pkg)!!
        val context = f.capture().context!!
        assertEquals(before.episodeUsageSeconds, context.session!!.episode.episodeUsageSeconds)
        assertEquals(gate.id, context.session.episode.pendingGate!!.gateId)
        assertEquals(before.nextReminderAtSeconds, context.session.episode.nextReminderAtSeconds)
        assertEquals(before, f.friction.snapshot(f.pkg))
        assertNull(context.session.admission.bypassExpiresAtElapsedMillis)
    }

    @Test fun unconfiguredPolicyIsInsufficientRatherThanAnEmptyDefaultPolicy() {
        val f = ShadowFixtures()
        f.policy = f.policy.copy(loaded = false)
        val observation = f.capture()
        assertNull(observation.context)
        assertEquals(setOf(ShadowFact.CONFIG_NOT_LOADED), observation.missing)
        assertEquals(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, f.completeLegacy(observation).status)
    }

    @Test fun unknownUsageAndUnknownEdgeAreNotReportedAsMismatch() {
        val usage = ShadowFixtures().also { it.usage = null }
        val missingUsage = usage.capture()
        assertTrue(ShadowFact.DAILY_USAGE_UNAVAILABLE in missingUsage.missing)
        assertEquals(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, usage.completeLegacy(missingUsage).status)
        val edge = ShadowFixtures().also { it.edgeAvailable = false }
        val missingEdge = edge.capture()
        assertFalse(missingEdge.context!!.daily!!.edge.observationAvailable)
        assertEquals(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, edge.completeLegacy(missingEdge).status)
    }

    @Test fun capabilitiesKeepTheExistingThreeStateAccessibilityMeaning() {
        val f = ShadowFixtures()
        f.health = f.health.copy(accessibilityConnected = false, overlayGranted = false)
        val context = f.capture().context!!
        assertEquals(f.health.accessibilityRuntimeState, context.capabilities.accessibility)
        assertFalse(context.capabilities.overlayGranted)
        assertEquals(ShadowComparisonStatus.MATCH, f.completeLegacy(f.capture()).status)
    }
}
