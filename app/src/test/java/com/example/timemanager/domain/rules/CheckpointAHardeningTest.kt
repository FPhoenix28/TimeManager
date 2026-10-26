package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.domain.focus.DeepFocusDisposition
import com.example.timemanager.domain.focus.DeepFocusEnforcementPolicy
import com.example.timemanager.domain.focus.DeepFocusEssentialApps
import com.example.timemanager.domain.focus.FocusEnforcementActionPolicy
import com.example.timemanager.domain.focus.FocusEnforcementPolicy
import com.example.timemanager.domain.profile.DailyRuleConfig
import com.example.timemanager.permissions.AccessibilityRuntimeState
import org.junit.Assert.*
import org.junit.Test

class CheckpointAHardeningTest {
    private val f = V090CompatibilityFixtures
    private val evaluator = RulesEvaluator()

    @Test fun ordinaryFocusDoesNotRequireApplicationOverlayPermission() {
        val base = f.context()
        val result = evaluator.evaluate(base.copy(session = null,
            focus = FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE)),
            capabilities = base.capabilities.copy(overlayGranted = false)))
        assertEquals(RuleKind.FOCUS, result.selected?.kind)
        assertTrue(result.requiredCapabilities.isEmpty())
        // Existing ordinary path calls this policy and uses TYPE_ACCESSIBILITY_OVERLAY.
        val state = FocusModeState(FocusModeType.FOCUS, startTimeMillis = 1L,
            endTimeMillis = 1_900_000_000_000L, blockedPackages = setOf(f.PACKAGE))
        val oldMatched = FocusEnforcementPolicy.shouldBlock(state, f.PACKAGE, "example.own", base.time.wallNowMillis)
        assertTrue(oldMatched)
        val oldAction = FocusEnforcementActionPolicy.decide(oldMatched, overlayShowing = false, debounced = false)
        assertTrue(oldAction.performHome && oldAction.showBlockedOverlay)
    }

    @Test fun stableOverLimitSuppressesSessionWithoutCreatingADailyDisplay() {
        val base = f.context()
        val context = base.copy(daily = f.daily(3_600L).copy(edge = DailyEdgeSnapshot(previousOverLimit = true)))
        val result = evaluator.evaluate(context)
        assertNull(result.selected)
        assertEquals(RuleKind.SESSION, result.suppressed.single().candidate.kind)
        assertFalse(result.suppressed.any { it.candidate.kind == RuleKind.DAILY })
        assertTrue(RuleKind.DAILY in result.activeConstraints)
        assertEquals(SuppressionReason.DAILY_ACTIVE, result.suppressed.single().reason)
    }

    private fun overContext() = f.context().copy(
        daily = f.daily(3_600L).copy(edge = DailyEdgeSnapshot(previousOverLimit = true)))

    @Test fun disconnectedFocusRequiresOnlyAnActualAccessibilityConnection() {
        val base = f.context()
        for (state in listOf(AccessibilityRuntimeState.DISABLED, AccessibilityRuntimeState.ENABLED_BUT_DISCONNECTED)) {
            val result = evaluator.evaluate(base.copy(session = null,
                focus = FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE)),
                capabilities = base.capabilities.copy(overlayGranted = false, accessibility = state)))
            assertEquals(RuleKind.FOCUS, result.selected?.kind)
            assertEquals(setOf(RequiredCapability.ACCESSIBILITY_CONNECTION), result.requiredCapabilities.map { it.capability }.toSet())
        }
    }

    @Test fun deepRequiresApplicationOverlayAndConnectionWithLegacySuspendParity() {
        val base = f.context()
        val state = FocusModeState(FocusModeType.DEEP_FOCUS, startTimeMillis = 1L, endTimeMillis = 1_900_000_000_000L)
        val essentials = DeepFocusEssentialApps("example.own", "launcher", "ime", null, emptySet(), "settings")
        for (overlayGranted in listOf(false, true)) for (connection in AccessibilityRuntimeState.entries) {
            val old = DeepFocusEnforcementPolicy.evaluate(state, f.PACKAGE, "example.MainActivity", essentials,
                keyguardLocked = false, overlayPermissionGranted = overlayGranted, nowMillis = base.time.wallNowMillis)
            assertEquals(if (overlayGranted) DeepFocusDisposition.BLOCK else DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY, old)
            val result = evaluator.evaluate(base.copy(session = null,
                focus = FocusRuntimeSnapshot.DeepFocus(1L, state.endTimeMillis, FocusModeStatus.ACTIVE),
                capabilities = base.capabilities.copy(overlayGranted = overlayGranted, accessibility = connection)))
            assertEquals(RuleKind.DEEP_FOCUS, result.selected?.kind) // Business match, not execution permission.
            val gaps = result.requiredCapabilities.map { it.capability }.toSet()
            assertEquals(!overlayGranted, RequiredCapability.OVERLAY in gaps)
            assertEquals(connection != AccessibilityRuntimeState.CONNECTED, RequiredCapability.ACCESSIBILITY_CONNECTION in gaps)
        }
    }

    @Test fun belowDailyLimitAllowsTheDueSession() {
        val result = evaluator.evaluate(overContext().copy(daily = f.daily(3_599L)))
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
    }

    @Test fun crossingSelectsDailyAndNamesDailyActiveSuppression() {
        val result = evaluator.evaluate(f.context().copy(daily = f.daily(3_600L)))
        assertEquals(RuleKind.DAILY, result.selected?.kind)
        assertTrue(RuleKind.DAILY in result.activeConstraints)
        assertEquals(SuppressionReason.DAILY_ACTIVE, result.suppressed.single().reason)
    }

    @Test fun suppliedExtensionReleasesDailySuppressionWithoutChangingTheEpisode() {
        val before = overContext()
        val after = before.copy(daily = before.daily!!.copy(todayExtensionSeconds = 900L))
        assertNull(evaluator.evaluate(before).selected)
        val result = evaluator.evaluate(after)
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
        assertSame(before.session, after.session)
        assertEquals(0L, before.daily!!.todayExtensionSeconds)
        assertEquals(900L, after.daily!!.todayExtensionSeconds)
    }

    @Test fun repeatedStableOverEvaluationDoesNotInventRetryOrMutateRuntime() {
        val context = overContext().let { it.copy(session = it.session!!.copy(
            admission = SessionAdmissionSnapshot(reentryGateArmed = true, bypassExpiresAtElapsedMillis = 5_000L))) }
        val before = context.copy()
        val first = evaluator.evaluate(context)
        repeat(3) {
            val result = evaluator.evaluate(context)
            assertNull(result.selected)
            assertEquals(first.activeConstraints, result.activeConstraints)
            assertEquals(first.suppressed, result.suppressed)
            assertEquals(SuppressionReason.DAILY_ACTIVE, result.suppressed.single().reason)
            assertEquals(context.identity, result.identity)
        }
        assertEquals(before, context)
        assertFalse(context.daily!!.edge.displayRetryReady)
        assertEquals(0, context.session!!.episode.reminderCount)
        assertEquals(5_000L, context.session.admission.bypassExpiresAtElapsedMillis)
        assertTrue(context.session.admission.reentryGateArmed)
    }

    @Test fun newSuppliedUsageDayReleasesDailyButKeepsTheSameSession() {
        val before = overContext()
        val day = UsageDayKey("2026-10-23")
        val after = before.copy(identity = before.identity.copy(usageDayKey = day),
            daily = f.daily(0L).copy(identity = UsageDayRuntimeIdentity(f.PACKAGE, day)),
            event = EvaluationEvent.USAGE_DAY_CHANGED)
        val result = evaluator.evaluate(after)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertSame(before.session, (result.selected as RuleCandidate.Session).runtime)
    }

    @Test fun missingDisplayEdgeDoesNotEraseKnownDailyActiveConstraint() {
        val base = overContext()
        val result = evaluator.evaluate(base.copy(daily = base.daily!!.copy(edge = DailyEdgeSnapshot(observationAvailable = false))))
        assertNull(result.selected)
        assertTrue(RuleKind.DAILY in result.activeConstraints)
        assertEquals(setOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_EDGE)), result.insufficientFacts)
        assertEquals(SuppressionReason.DAILY_ACTIVE, result.suppressed.single().reason)
    }

    @Test fun unsetAndZeroQuotaKeepExistingEnforcementMeaning() {
        val base = overContext()
        val zero = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(daily = DailyRuleConfig.Explicit(0L)))
        val noUse = evaluator.evaluate(base.copy(effectivePolicy = zero, daily = f.daily(0L)))
        assertEquals(RuleKind.SESSION, noUse.selected?.kind)
        assertFalse(RuleKind.DAILY in noUse.activeConstraints)
        val used = evaluator.evaluate(base.copy(effectivePolicy = zero, daily = base.daily!!.copy(usageSeconds = 1L)))
        assertTrue(RuleKind.DAILY in used.activeConstraints)
        assertNull(used.selected)
        val unset = zero.copy(appPolicy = zero.appPolicy.copy(daily = DailyRuleConfig.Unset))
        val result = evaluator.evaluate(base.copy(effectivePolicy = unset))
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
    }

    @Test fun focusRemainsHigherPriorityThanStableActiveDaily() {
        val context = overContext().copy(focus = FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE)))
        val result = evaluator.evaluate(context)
        assertEquals(RuleKind.FOCUS, result.selected?.kind)
        assertTrue(RuleKind.DAILY in result.activeConstraints)
        assertEquals(SuppressionReason.HIGHER_PRIORITY_CANDIDATE, result.suppressed.single().reason)
    }

    @Test fun unknownDailyAccountingDoesNotCreateAFalseActiveConstraint() {
        val result = evaluator.evaluate(overContext().copy(daily = null))
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
        assertEquals(setOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)), result.insufficientFacts)
    }

    @Test fun increasedSuppliedBaseLimitReleasesSuppressionWithoutAnExtensionWrite() {
        val before = overContext()
        val newPolicy = before.effectivePolicy.copy(appPolicy = before.effectivePolicy.appPolicy.copy(daily = DailyRuleConfig.Explicit(5_400L)))
        val result = evaluator.evaluate(before.copy(effectivePolicy = newPolicy))
        assertEquals(RuleKind.SESSION, result.selected?.kind)
        assertFalse(RuleKind.DAILY in result.activeConstraints)
        assertEquals(0L, before.daily!!.todayExtensionSeconds)
        assertSame(before.session, (result.selected as RuleCandidate.Session).runtime)
    }
}
