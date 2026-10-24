package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState
import org.junit.Assert.*
import org.junit.Test

class AccessibilityAuthorityRulesTest {
    private val authority = AccessibilityRulesAuthority()
    private fun plan(input: AccessibilityRulesSnapshot) = authority.decide(input) as AccessibilityAuthorityDecision.Engine
    private fun window(focus: FocusModeState = activeFocus(), target: TargetDisposition = TargetDisposition.ORDINARY_APP) =
        accessibilitySnapshot(focus, target, scope = AccessibilityObservationScope.FOCUS_DEEP_WINDOW)
    private fun intent(input: AccessibilityRulesSnapshot) = plan(input).projection.intent

    @Test fun f1BlockedFocus() { assertEquals(AccessibilityIntent.BLOCK_FOCUS, intent(window())) }
    @Test fun f2UnblockedFocus() {
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus().copy(blockedPackages = setOf("other")))))
    }
    @Test fun f3OwnFocusExempt() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(target = TargetDisposition.OWN_APP))) }
    @Test fun f4UnselectedLauncherNotBlocked() {
        assertEquals(AccessibilityIntent.ALLOW_ENTRY,
            intent(window(activeFocus().copy(blockedPackages = emptySet()), TargetDisposition.LAUNCHER)))
    }
    @Test fun f5FocusDoesNotRequireApplicationOverlay() {
        assertEquals(AccessibilityIntent.BLOCK_FOCUS,
            intent(window().copy(health = PermissionHealthState(true, true, false, true, true))))
    }
    @Test fun f6DisconnectedFocusCannotClaimProtection() {
        val result = plan(window().copy(health = PermissionHealthState(true, true, true, true, false)))
        assertEquals(AccessibilityIntent.FAIL_OPEN, result.projection.intent)
        assertEquals(RuleKind.FOCUS, result.evaluation.selected!!.kind)
    }
    @Test fun f7EndedFocus() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus().copy(endTimeMillis = 2_000L)))) }

    @Test fun d1OrdinaryDeep() { assertEquals(AccessibilityIntent.BLOCK_DEEP, intent(window(activeFocus(FocusModeType.DEEP_FOCUS)))) }
    @Test fun d2OwnDeepExempt() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.OWN_APP))) }
    @Test fun d3LauncherDeepExempt() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.LAUNCHER))) }
    @Test fun d4SystemUiDeepExempt() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.SYSTEM_UI))) }
    @Test fun d5RecoveryDeepExempt() { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.RECOVERY_SETTINGS_UI))) }
    @Test fun d6DeepMissingOverlayIsEngineCapabilityNotFallback() {
        val result = plan(window(activeFocus(FocusModeType.DEEP_FOCUS)).copy(health = PermissionHealthState(true, true, false, true, true)))
        assertEquals(RuleKind.DEEP_FOCUS, result.evaluation.selected!!.kind)
        assertEquals(AccessibilityIntent.FAIL_OPEN, result.projection.intent)
        assertTrue(RequiredCapability.OVERLAY in result.projection.missingCapabilities)
    }
    @Test fun d7ExactDeepEndInactive() { assertEquals(AccessibilityIntent.ALLOW_ENTRY,
        intent(window(activeFocus(FocusModeType.DEEP_FOCUS).copy(endTimeMillis = 2_000L)))) }
    @Test fun d8UnknownIsExplicitMissingNotEssential() {
        val result = authority.decide(window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.UNCLASSIFIED))
        assertTrue(result is AccessibilityAuthorityDecision.LegacyFallback)
    }

    @Test fun deepExemptFreshEntryRetainsDailyAndSuppressedSession() {
        val result = plan(accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.NECESSARY_CALL_UI,
            daily = DailyLimitSnapshot("day", 60L, 60L, true)))
        assertEquals(AccessibilityIntent.SUPPRESS_SESSION_DAILY, result.projection.intent)
        assertTrue(result.evaluation.suppressed.any { it.candidate.kind == RuleKind.SESSION })
    }
    @Test fun deepExemptFreshEntryRetainsSession() {
        assertEquals(AccessibilityIntent.CHECK_SESSION_REENTRY,
            intent(accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.RECOVERY_SETTINGS_UI)))
    }
    @Test fun genericDeepExemptionNeverExecutesCachedLowerRules() {
        val input = window(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.NECESSARY_CALL_UI)
            .copy(daily = DailyLimitSnapshot("day", 60L, 60L, true), dailyFreshForEntry = false)
        val result = plan(input)
        assertTrue(RuleKind.DAILY in result.evaluation.activeConstraints)
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, result.projection.intent)
        assertTrue(result.projection.deferredToMonitor)
    }
    @Test fun r1DailyWinsOverArmedSessionWithoutMutation() {
        val input = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 60L, 60L, true))
        val before = input.episode
        repeat(20) { assertEquals(AccessibilityIntent.SUPPRESS_SESSION_DAILY, intent(input)) }
        assertEquals(before, input.episode)
    }
    @Test fun r2ExtensionReleasesToSameEpisode() {
        val input = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 60L, 90L, false))
        assertEquals(AccessibilityIntent.CHECK_SESSION_REENTRY, intent(input))
        assertEquals(1, input.episode!!.reminderCount)
    }
    @Test fun r3CachedDailyDiscarded() {
        assertEquals(AccessibilityAuthorityDecision.Stale, authority.decide(accessibilitySnapshot().copy(dailyFreshForEntry = false)))
    }
    @Test fun r7ArmedStageRequestsSession() { assertEquals(AccessibilityIntent.CHECK_SESSION_REENTRY, intent(accessibilitySnapshot())) }
    @Test fun r8LiveBypassNotConsumed() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(bypassExpiresAtMillis = 2_000L)) }
        repeat(20) { assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(input)) }
        assertEquals(2_000L, input.episode!!.bypassExpiresAtMillis)
    }
    @Test fun r9AdmittedVisitNotGated() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(allowedForeground = true)) }
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(input))
    }
    @Test fun r10PendingGateNotDuplicated() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(
            pendingGate = SessionGate(2L, SessionGatePhase.REENTRY, 5_000L))) }
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, intent(input))
        assertEquals(2L, input.episode!!.pendingGate!!.id)
    }
    @Test fun r11ExpiredBypassDoesNotSuppressOrGetCleared() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(bypassExpiresAtMillis = 1_000L)) }
        assertEquals(AccessibilityIntent.CHECK_SESSION_REENTRY, intent(input))
        assertEquals(1_000L, input.episode!!.bypassExpiresAtMillis)
    }
    @Test fun normalThresholdWithoutArmedStageStaysMonitorOwned() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(episodeUsageSeconds = 40L, reentryGateArmed = false)) }
        val result = plan(input)
        assertTrue(result.projection.deferredToMonitor)
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, result.projection.intent)
    }
    @Test fun higherRuleRankingRemainsResolverOwned() {
        for ((mode, expected) in listOf(FocusModeType.FOCUS to RuleKind.FOCUS, FocusModeType.DEEP_FOCUS to RuleKind.DEEP_FOCUS)) {
            val result = plan(accessibilitySnapshot(activeFocus(mode), daily = DailyLimitSnapshot("day", 60L, 60L, true)))
            assertEquals(expected, result.evaluation.selected!!.kind)
            assertTrue(result.evaluation.suppressed.any { it.candidate.kind == RuleKind.DAILY })
            assertTrue(result.evaluation.suppressed.any { it.candidate.kind == RuleKind.SESSION })
        }
    }
}
