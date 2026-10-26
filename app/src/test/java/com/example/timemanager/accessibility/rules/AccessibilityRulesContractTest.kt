package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState
import org.junit.Assert.*
import org.junit.Test

internal fun accessibilitySnapshot(
    focus: FocusModeState = FocusModeState(),
    disposition: TargetDisposition = TargetDisposition.ORDINARY_APP,
    managed: Boolean = true,
    daily: DailyLimitSnapshot? = DailyLimitSnapshot("day", 20L, 60L, false),
    episode: SessionFrictionState? = SessionFrictionState("app", 20L, 35L,
        episodeUsageSeconds = 20L, reminderCount = 1, isActive = true,
        lastEngagementTimeMillis = 100L, reentryGateArmed = true),
    health: PermissionHealthState = PermissionHealthState(true, true, true, true, true),
    scope: AccessibilityObservationScope = AccessibilityObservationScope.REENTRY,
) = AccessibilityRulesSnapshot(
    identity = EvaluationIdentity("app", ForegroundGeneration(7L), PolicyRevision(1L), UsageDayKey("day"), 1L),
    target = TargetFacts("app", "AppActivity", disposition),
    time = EvaluationTime(2_000L, 1_000L), focus = focus,
    policy = AccessibilityPolicyFacts(true, managed, 60L, 20L),
    daily = daily, dailyFreshForEntry = true, episode = episode, health = health, scope = scope,
)

internal fun activeFocus(mode: FocusModeType = FocusModeType.FOCUS) = FocusModeState(
    mode, startTimeMillis = 1_000L, endTimeMillis = 10_000L, blockedPackages = setOf("app"),
)

class AccessibilityRulesContractTest {
    @Test fun ordinaryFocusDoesNotNeedApplicationOverlayOrManagedMembership() {
        val input = accessibilitySnapshot(focus = activeFocus(), managed = false,
            health = PermissionHealthState(true, true, false, true, true))
        val evaluation = RulesEvaluator().evaluate(AccessibilityRulesSnapshotAdapter().adapt(input).context)
        assertEquals(RuleKind.FOCUS, evaluation.selected?.kind)
        assertTrue(evaluation.requiredCapabilities.isEmpty())
        assertEquals(AccessibilityIntent.BLOCK_FOCUS, AccessibilityRulesCompatibilityProjection.project(evaluation).intent)
    }

    @Test fun deepExemptionKeepsLowerDailyAndSessionFacts() {
        val input = accessibilitySnapshot(focus = activeFocus(FocusModeType.DEEP_FOCUS),
            disposition = TargetDisposition.NECESSARY_CALL_UI,
            daily = DailyLimitSnapshot("day", 60L, 60L, true))
        val evaluation = RulesEvaluator().evaluate(AccessibilityRulesSnapshotAdapter().adapt(input).context)
        assertEquals(RuleKind.DAILY, evaluation.selected?.kind)
        assertTrue(evaluation.explicitExemptions.any { it.rule == RuleKind.DEEP_FOCUS })
        assertTrue(evaluation.suppressed.any { it.candidate.kind == RuleKind.SESSION })
    }

    @Test fun liveBypassIsReadWithoutConsumption() {
        val input = accessibilitySnapshot().let { it.copy(episode = it.episode!!.copy(bypassExpiresAtMillis = 2_000L)) }
        val result = AccessibilityRulesShadow().compare(input, AccessibilityIntent.ALLOW_ENTRY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(2_000L, input.episode!!.bypassExpiresAtMillis)
        assertEquals(1, input.episode.reminderCount)
    }
}
