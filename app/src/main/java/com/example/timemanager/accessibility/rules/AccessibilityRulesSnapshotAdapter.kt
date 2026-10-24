package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.intervention.DailyReadObservation
import com.example.timemanager.domain.intervention.ForegroundEntry
import com.example.timemanager.domain.profile.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState

enum class AccessibilityObservationScope { FOCUS_DEEP_WINDOW, REENTRY }
enum class AccessibilityMissingFact { CONFIGURATION, DAILY_NOT_FRESH, DAILY_ACCOUNTING, POLICY_COHERENCE, CAPABILITY_INCONSISTENCY }

object AccessibilityEntryFreshness {
    fun matches(entry: ForegroundEntry, previousSequence: Long, usageDayKey: String, publication: DailyReadObservation?): Boolean =
        publication != null && publication.entry == entry && publication.sequence > previousSequence &&
            publication.snapshot.usageDayKey == usageDayKey
}

data class AccessibilityPolicyFacts(
    val loaded: Boolean,
    val managed: Boolean,
    val baseLimitSeconds: Long?,
    val firstReminderSeconds: Long?,
)

/** Immutable observations, not another Focus/Episode/Daily state owner. */
data class AccessibilityRulesSnapshot(
    val identity: EvaluationIdentity,
    val target: TargetFacts,
    val time: EvaluationTime,
    val focus: FocusModeState,
    val policy: AccessibilityPolicyFacts,
    val daily: DailyLimitSnapshot?,
    val dailyFreshForEntry: Boolean,
    val episode: SessionFrictionState?,
    val health: PermissionHealthState,
    val scope: AccessibilityObservationScope,
    val overlayOccupied: Boolean = false,
    val dailyAlreadyHandled: Boolean = false,
    val capabilityInconsistent: Boolean = false,
)

data class AdaptedAccessibilitySnapshot(
    val context: RuleEvaluationContext,
    val missing: Set<AccessibilityMissingFact>,
)

class AccessibilityRulesSnapshotAdapter {
    fun adapt(input: AccessibilityRulesSnapshot): AdaptedAccessibilitySnapshot {
        val base = input.policy.baseLimitSeconds
        val day = input.daily?.takeIf { it.usageDayKey == input.identity.usageDayKey.value }
        val missing = buildSet {
            if (!input.policy.loaded) add(AccessibilityMissingFact.CONFIGURATION)
            if (input.capabilityInconsistent) add(AccessibilityMissingFact.CAPABILITY_INCONSISTENCY)
            if (input.scope == AccessibilityObservationScope.REENTRY && !input.dailyFreshForEntry)
                add(AccessibilityMissingFact.DAILY_NOT_FRESH)
            if (input.policy.managed && base != null && day == null) add(AccessibilityMissingFact.DAILY_ACCOUNTING)
            if (day != null && ((base == null) != (day.effectiveLimitSeconds == null) ||
                    base != null && day.effectiveLimitSeconds != null && day.effectiveLimitSeconds < base))
                add(AccessibilityMissingFact.POLICY_COHERENCE)
        }
        val daily = if (base != null && day?.effectiveLimitSeconds != null && day.effectiveLimitSeconds >= base) {
            DailyRuntimeSnapshot(UsageDayRuntimeIdentity(input.identity.packageName, input.identity.usageDayKey),
                day.usedSeconds, day.effectiveLimitSeconds - base,
                // APP_ENTERED is the observed entry, not a consumed LimitInterventionManager edge.
                edge = DailyEdgeSnapshot(previousOverLimit = true,
                    enteredThisVisit = input.scope == AccessibilityObservationScope.REENTRY))
        } else null
        val session = input.episode?.let { e ->
            SessionRuntimeSnapshot(e.packageName,
                SessionEpisodeSnapshot(e.episodeUsageSeconds, e.firstReminderSeconds, e.reminderCount,
                    e.nextReminderAtSeconds, e.lastEngagementTimeMillis, e.pendingGate?.let {
                        PendingSessionGateSnapshot(it.id, it.phase, it.endTimeMillis, it.reminderCommitted)
                    }), null, SessionAdmissionSnapshot(e.reentryGateArmed, e.bypassExpiresAtMillis, e.allowedForeground))
        }
        val state = input.focus
        val focus = if (!state.hasValidTimeWindow) FocusRuntimeSnapshot.Off else when (state.mode) {
            FocusModeType.OFF -> FocusRuntimeSnapshot.Off
            FocusModeType.FOCUS -> FocusRuntimeSnapshot.Focus(state.startTimeMillis, state.endTimeMillis,
                state.blockedPackages, state.status)
            FocusModeType.DEEP_FOCUS -> FocusRuntimeSnapshot.DeepFocus(state.startTimeMillis, state.endTimeMillis, state.status)
        }
        return AdaptedAccessibilitySnapshot(RuleEvaluationContext(input.identity, input.target, input.time,
            EffectiveAppPolicy(ProfileId.DEFAULT, AppPolicy(input.identity.packageName, input.policy.managed,
                base?.let(DailyRuleConfig::Explicit) ?: DailyRuleConfig.Unset,
                SessionRuleConfig(input.policy.firstReminderSeconds?.let(FirstSessionReminder::Explicit)
                    ?: FirstSessionReminder.InheritedDefault))),
            daily, session, focus,
            CapabilitySnapshot(input.health.usageAccessGranted, input.health.overlayGranted,
                input.health.notificationGranted, input.health.accessibilityRuntimeState),
            if (input.scope == AccessibilityObservationScope.REENTRY) EvaluationEvent.APP_ENTERED else EvaluationEvent.TICK), missing)
    }
}
