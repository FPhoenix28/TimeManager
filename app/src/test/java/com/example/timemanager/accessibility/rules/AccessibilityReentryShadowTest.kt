package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState
import org.junit.Assert.*
import org.junit.Test

class AccessibilityReentryShadowTest {
    private val shadow = AccessibilityRulesShadow()
    @Test fun armedStageIsEligible() {
        val result = shadow.compare(accessibilitySnapshot(), AccessibilityIntent.CHECK_SESSION_REENTRY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(SessionCandidateReason.REENTRY_GATE_ELIGIBLE, result.evaluation!!.selected!!.reason)
    }
    @Test fun noGateDoesNotManufactureGate() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(reentryGateArmed = false)) }
        assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun absentEpisodeDoesNotCreateOne() {
        assertEquals(AccessibilityShadowStatus.MATCH,
            shadow.compare(accessibilitySnapshot(episode = null), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun freshDailyWinsAndDoesNotCommitOrConsumeSession() {
        val input = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 60L, 60L, true))
        val before = input.episode
        val result = shadow.compare(input, AccessibilityIntent.SUPPRESS_SESSION_DAILY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(RuleKind.DAILY, result.evaluation!!.selected!!.kind)
        assertEquals(before, input.episode)
        assertTrue(input.episode!!.reentryGateArmed)
    }
    @Test fun extensionReleasesDailyWithoutRestartingEpisode() {
        val reached = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 60L, 60L, true))
        val released = reached.copy(daily = DailyLimitSnapshot("day", 60L, 90L, false))
        val result = shadow.compare(released, AccessibilityIntent.CHECK_SESSION_REENTRY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertFalse(result.evaluation!!.activeConstraints.contains(RuleKind.DAILY))
        assertEquals(reached.episode, released.episode)
    }
    @Test fun focusSuppressesGateWithoutCommittingStage() {
        val input = accessibilitySnapshot(focus = activeFocus())
        val result = shadow.compare(input, AccessibilityIntent.BLOCK_FOCUS)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertTrue(result.evaluation!!.suppressed.any { it.candidate.kind == RuleKind.SESSION })
        assertEquals(1, input.episode!!.reminderCount)
    }
    @Test fun deepSuppressesDailyAndSession() {
        val result = shadow.compare(accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS),
            daily = DailyLimitSnapshot("day", 60L, 60L, true)), AccessibilityIntent.BLOCK_DEEP)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(setOf(RuleKind.DAILY, RuleKind.SESSION), result.evaluation!!.suppressed.map { it.candidate.kind }.toSet())
    }
    @Test fun deepExemptionAllowsDailyToWinEvenInEntryContext() {
        val input = accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.NECESSARY_CALL_UI,
            daily = DailyLimitSnapshot("day", 60L, 60L, true))
        assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input, AccessibilityIntent.SUPPRESS_SESSION_DAILY).status)
    }
    @Test fun expiredBypassDoesNotSuppressGateAndIsNotMutated() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(bypassExpiresAtMillis = 1_000L)) }
        assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY).status)
        assertEquals(1_000L, input.episode!!.bypassExpiresAtMillis)
    }
    @Test fun admittedVisitDoesNotRequestDuplicateGate() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(allowedForeground = true)) }
        assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun alreadyPendingGateIsNotCreatedAgain() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(pendingGate = SessionGate(2L, SessionGatePhase.REENTRY, 5_000L))) }
        assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY).status)
        assertEquals(2L, input.episode!!.pendingGate!!.id)
    }
    @Test fun disconnectedOrMissingUsageOrOverlayCannotExecuteReentry() {
        for (health in listOf(PermissionHealthState(true, true, true, true, false),
            PermissionHealthState(false, true, true, true, true), PermissionHealthState(true, true, false, true, true))) {
            assertEquals(AccessibilityShadowStatus.MATCH,
                shadow.compare(accessibilitySnapshot(health = health), AccessibilityIntent.FAIL_OPEN).status)
            assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
                shadow.compare(accessibilitySnapshot(health = health), AccessibilityIntent.CHECK_SESSION_REENTRY).status)
        }
    }
    @Test fun cachedDailyIsNotPromotedToFreshEntryEvidence() {
        val input = accessibilitySnapshot().copy(dailyFreshForEntry = false)
        assertEquals(AccessibilityShadowStatus.INSUFFICIENT_FACTS,
            shadow.compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY).status)
    }
    @Test fun noDailySettingIsNotSynthesizedFromTheOldTestQuota() {
        val source = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 100L, null, false))
        val input = source.copy(policy = source.policy.copy(baseLimitSeconds = null))
        val result = shadow.compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertFalse(result.evaluation!!.activeConstraints.contains(RuleKind.DAILY))
    }
    @Test fun normalSessionDueRemainsMonitorExecutionNotAReentryGate() {
        val source = accessibilitySnapshot()
        val input = source.copy(episode = source.episode!!.copy(reentryGateArmed = false, episodeUsageSeconds = 35L))
        val result = shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY)
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, result.status)
        assertTrue(result.projection!!.deferredToMonitor)
    }
    @Test fun dailyAlreadyHandledObservationNeverBecomesASecondCommand() {
        val input = accessibilitySnapshot(daily = DailyLimitSnapshot("day", 60L, 60L, true)).copy(dailyAlreadyHandled = true)
        val result = shadow.compare(input, AccessibilityIntent.SUPPRESS_SESSION_DAILY, currentGeneration = ForegroundGeneration(8L))
        assertEquals(AccessibilityShadowStatus.STALE, result.status)
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
            shadow.compare(input, AccessibilityIntent.SUPPRESS_SESSION_DAILY).status)
        assertEquals(1, input.episode!!.reminderCount)
    }
}
