package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.EffectiveAppLimit
import com.example.timemanager.data.model.SessionFrictionState
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.domain.profile.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState

data class LegacyPolicyInput(
    val loaded: Boolean,
    val managed: Boolean,
    val dailyLimitMinutes: Int?,
    val storedSessionReminderMinutes: Int?,
    val dailyLimitSeconds: Long? = null,
    val storedSessionReminderSeconds: Long? = null,
)

enum class ShadowGenerationSource { COORDINATOR, MONITOR_OBSERVATION }
enum class ShadowFact {
    CONFIG_NOT_LOADED, DAILY_USAGE_UNAVAILABLE, DAILY_LIMIT_UNAVAILABLE, DAILY_EDGE_UNAVAILABLE,
    POLICY_INPUT_CHANGED, SESSION_CHANGED_DURING_READ, FOREGROUND_CHANGED_DURING_READ,
}

data class ShadowAvailability(
    val canShowSoft: Boolean,
    val interventionsAllowed: Boolean,
    val overlayOccupied: Boolean,
    val dailyDisplayOccupied: Boolean = false,
)

/** A single read-only Monitor observation, not another runtime/config owner. */
data class LegacyMonitorSnapshot(
    val identity: EvaluationIdentity,
    val generationSource: ShadowGenerationSource,
    val ownPackage: String,
    val time: EvaluationTime,
    val policy: LegacyPolicyInput,
    val usageSeconds: Long?,
    val effectiveLimit: EffectiveAppLimit?,
    val dailyOwner: LimitInterventionManager.RulesReadSnapshot?,
    val episode: SessionFrictionState?,
    val physicalSessionSeconds: Long?,
    val health: PermissionHealthState,
    val legacyReached: Boolean?,
    val legacySessionDue: Boolean,
    val availability: ShadowAvailability,
    val readIssues: Set<ShadowFact> = emptySet(),
)

data class AdaptedRulesSnapshot(val context: RuleEvaluationContext?, val missing: Set<ShadowFact>)

/** Projects only Monitor Daily/Session facts; Focus/Deep classification is outside this slice. */
class LegacyRulesSnapshotAdapter {
    fun adapt(input: LegacyMonitorSnapshot): AdaptedRulesSnapshot {
        if (!input.policy.loaded) return AdaptedRulesSnapshot(null, setOf(ShadowFact.CONFIG_NOT_LOADED))
        val dailyConfig = (input.policy.dailyLimitSeconds ?: input.policy.dailyLimitMinutes?.times(60L))
            ?.let { DailyRuleConfig.Explicit(it) } ?: DailyRuleConfig.Unset
        val issues = buildSet {
            addAll(input.readIssues)
            if (dailyConfig is DailyRuleConfig.Explicit) {
                if (input.usageSeconds == null) add(ShadowFact.DAILY_USAGE_UNAVAILABLE)
                if (input.effectiveLimit == null) add(ShadowFact.DAILY_LIMIT_UNAVAILABLE)
                if (input.dailyOwner == null) add(ShadowFact.DAILY_EDGE_UNAVAILABLE)
                if (input.effectiveLimit != null && input.effectiveLimit.baseLimitSeconds != dailyConfig.baseLimitSeconds) {
                    add(ShadowFact.POLICY_INPUT_CHANGED)
                }
            }
        }
        val sessionConfig = SessionRuleConfig((input.policy.storedSessionReminderSeconds ?:
            input.policy.storedSessionReminderMinutes?.times(60L))?.let {
            FirstSessionReminder.Explicit(it)
        } ?: FirstSessionReminder.InheritedDefault)
        val daily = if (dailyConfig is DailyRuleConfig.Explicit && input.usageSeconds != null && input.effectiveLimit != null) {
            DailyRuntimeSnapshot(
                UsageDayRuntimeIdentity(input.identity.packageName, input.identity.usageDayKey),
                input.usageSeconds, input.effectiveLimit.todayExtensionSeconds,
                // Notification receipts are not observed/compared in this intervention-only slice.
                edge = input.dailyOwner?.let {
                    DailyEdgeSnapshot(it.previousOverLimit, it.enteredThisVisit, it.retryReady)
                } ?: DailyEdgeSnapshot(observationAvailable = false),
            )
        } else null
        val session = input.episode?.let { state ->
            SessionRuntimeSnapshot(state.packageName,
                SessionEpisodeSnapshot(state.episodeUsageSeconds, state.firstReminderSeconds, state.reminderCount,
                    state.nextReminderAtSeconds, state.lastEngagementTimeMillis, state.pendingGate?.let {
                        PendingSessionGateSnapshot(it.id, it.phase, it.endTimeMillis, it.reminderCommitted)
                    }),
                input.physicalSessionSeconds?.let { PhysicalSessionSnapshot(it, actuallyInForeground = true) },
                SessionAdmissionSnapshot(state.reentryGateArmed, state.bypassExpiresAtMillis, state.allowedForeground))
        }
        val context = RuleEvaluationContext(
            input.identity,
            TargetFacts(input.identity.packageName, disposition = if (input.identity.packageName == input.ownPackage)
                TargetDisposition.OWN_APP else TargetDisposition.UNCLASSIFIED),
            input.time,
            EffectiveAppPolicy(ProfileId.DEFAULT, AppPolicy(input.identity.packageName, input.policy.managed, dailyConfig, sessionConfig)),
            daily, session,
            // Scope projection only: this does NOT assert that the real Focus runtime is Off.
            FocusRuntimeSnapshot.Off,
            CapabilitySnapshot(input.health.usageAccessGranted, input.health.overlayGranted,
                input.health.notificationGranted, input.health.accessibilityRuntimeState),
            EvaluationEvent.TICK, // Physical-session start is not an Accessibility re-entry event.
        )
        return AdaptedRulesSnapshot(context, issues)
    }
}
