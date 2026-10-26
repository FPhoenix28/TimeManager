package com.example.timemanager.domain.rules

enum class RuleKind { DEEP_FOCUS, FOCUS, DAILY, SESSION }
sealed interface CandidateReason
enum class DailyCandidateReason : CandidateReason { LIMIT_CROSSED, ENTERED_WHILE_OVER_LIMIT, DISPLAY_RETRY }
enum class SessionCandidateReason : CandidateReason { EPISODE_THRESHOLD_REACHED, REENTRY_GATE_ELIGIBLE }
enum class FocusCandidateReason : CandidateReason { SELECTED_APP_BLOCKED, DEEP_FOCUS_ORDINARY_APP }

/** Representation only: constructing a candidate does not evaluate priority or execute it. */
sealed interface RuleCandidate {
    val kind: RuleKind
    val reason: CandidateReason

    data class Daily(
        val baseLimitSeconds: Long,
        val runtime: DailyRuntimeSnapshot,
        override val reason: DailyCandidateReason,
    ) : RuleCandidate {
        override val kind: RuleKind get() = RuleKind.DAILY
        init { require(baseLimitSeconds >= 0L) }
    }

    data class Session(
        val runtime: SessionRuntimeSnapshot,
        override val reason: SessionCandidateReason,
    ) : RuleCandidate {
        override val kind: RuleKind get() = RuleKind.SESSION
    }

    data class Focus(val endWallMillis: Long) : RuleCandidate {
        override val kind: RuleKind get() = RuleKind.FOCUS
        override val reason: FocusCandidateReason get() = FocusCandidateReason.SELECTED_APP_BLOCKED
    }

    data class DeepFocus(val endWallMillis: Long) : RuleCandidate {
        override val kind: RuleKind get() = RuleKind.DEEP_FOCUS
        override val reason: FocusCandidateReason get() = FocusCandidateReason.DEEP_FOCUS_ORDINARY_APP
    }
}

enum class SuppressionReason { HIGHER_PRIORITY_CANDIDATE, DAILY_ACTIVE }
data class SuppressedCandidate(val candidate: RuleCandidate, val reason: SuppressionReason)

/** Rule-scoped exemption: allowing an essential target for Deep does not erase other facts. */
data class RuleExemption(val rule: RuleKind, val targetDisposition: TargetDisposition)
enum class MissingRuleFact { DAILY_USAGE, DAILY_EDGE, FOREGROUND_OBSERVATION, TARGET_CLASSIFICATION }
enum class RequiredCapability { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, ACCESSIBILITY_CONNECTION }

data class RuleFactGap(val rule: RuleKind, val fact: MissingRuleFact)

/** An unmet prerequisite of this candidate, not a claim that execution succeeded. */
data class CandidateCapabilityGap(val candidate: RuleCandidate, val capability: RequiredCapability)

/** No selected candidate alone does not prove Allow: exemptions and missing facts stay explicit. */
class RuleEvaluation(
    val identity: EvaluationIdentity,
    val selected: RuleCandidate?,
    suppressed: Collection<SuppressedCandidate> = emptyList(),
    explicitExemptions: Collection<RuleExemption> = emptyList(),
    insufficientFacts: Collection<RuleFactGap> = emptyList(),
    requiredCapabilities: Collection<CandidateCapabilityGap> = emptyList(),
    activeConstraints: Collection<RuleKind> = listOfNotNull(selected?.kind) + suppressed.map { it.candidate.kind },
) {
    val suppressed: List<SuppressedCandidate> = buildList { addAll(suppressed) }
    val explicitExemptions: List<RuleExemption> = buildList { addAll(explicitExemptions) }
    val insufficientFacts: Set<RuleFactGap> = buildSet { addAll(insufficientFacts) }
    val requiredCapabilities: Set<CandidateCapabilityGap> = buildSet { addAll(requiredCapabilities) }
    /** Business constraints may remain active without a newly eligible display candidate. */
    val activeConstraints: Set<RuleKind> = buildSet { addAll(activeConstraints) }
}
