package com.example.timemanager.domain.rules

import com.example.timemanager.domain.profile.EffectiveAppPolicy

@JvmInline
value class ForegroundGeneration(val value: Long) {
    init { require(value >= 0L) }
}

@JvmInline
value class PolicyRevision(val value: Long) {
    init { require(value >= 0L) }
}

/** Supplied by the future adapter; no counters or clock reads are owned by these contracts. */
data class EvaluationIdentity(
    val packageName: String,
    val foregroundGeneration: ForegroundGeneration,
    val policyRevision: PolicyRevision,
    val usageDayKey: UsageDayKey,
    val evaluationSequence: Long,
) {
    init { require(packageName.isNotBlank() && evaluationSequence >= 0L) }
}

enum class TargetDisposition {
    UNCLASSIFIED, ORDINARY_APP, OWN_APP, LAUNCHER, INPUT_METHOD, SYSTEM_UI,
    NECESSARY_CALL_UI, RECOVERY_SETTINGS_UI, LOCK_SCREEN,
}

/** Android adapters will resolve packages/classes into these facts without moving OS APIs here. */
data class TargetFacts(
    val packageName: String,
    val className: String? = null,
    val disposition: TargetDisposition = TargetDisposition.UNCLASSIFIED,
) {
    init { require(packageName.isNotBlank()) }
}

data class EvaluationTime(val wallNowMillis: Long, val elapsedNowMillis: Long) {
    init { require(elapsedNowMillis >= 0L) }
}

enum class EvaluationEvent {
    TICK, APP_ENTERED, CONFIG_CHANGED, USER_CONTINUE, USER_EXIT,
    CAPABILITY_CHANGED, USAGE_DAY_CHANGED,
}

/** Rule facts only. Window ownership, attachment and command outcomes belong to execution. */
data class RuleEvaluationContext(
    val identity: EvaluationIdentity,
    val target: TargetFacts,
    val time: EvaluationTime,
    val effectivePolicy: EffectiveAppPolicy,
    /** null means current daily accounting facts are unavailable, not zero use. */
    val daily: DailyRuntimeSnapshot?,
    /** null means no existing episode for this target. */
    val session: SessionRuntimeSnapshot?,
    val focus: FocusRuntimeSnapshot,
    val capabilities: CapabilitySnapshot,
    val event: EvaluationEvent,
) {
    init {
        require(target.packageName == identity.packageName)
        require(effectivePolicy.appPolicy.packageName == identity.packageName)
        require(daily == null || daily.identity == UsageDayRuntimeIdentity(identity.packageName, identity.usageDayKey))
        require(session == null || session.packageName == identity.packageName)
    }
}
