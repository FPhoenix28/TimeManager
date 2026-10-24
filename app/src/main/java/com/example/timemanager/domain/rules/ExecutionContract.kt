package com.example.timemanager.domain.rules

enum class ExecutionKind { SHOW_IN_PLACE, HOME_THEN_SHOW, ALLOW, DEGRADED_SOFT_INTERVENTION, FAIL_OPEN }

data class OverlayOwnershipSnapshot(val rule: RuleKind, val packageName: String) {
    init { require(packageName.isNotBlank()) }
}

/** Planner input only; an occupied window never changes whether a quota rule is true. */
data class ExecutionContextSnapshot(
    val overlayOwner: OverlayOwnershipSnapshot? = null,
    val overlayAttached: Boolean = false,
    val homeCommandAvailable: Boolean = false,
    val activeExecutionKind: ExecutionKind? = null,
    val pendingDecision: EvaluationIdentity? = null,
    val pendingCommandDeadlineElapsedMillis: Long? = null,
)

/** A proposed action, not proof of HOME, attachment, or a committed session stage. */
data class ExecutionProposal(
    val identity: EvaluationIdentity,
    val kind: ExecutionKind,
    val candidate: RuleCandidate?,
)

enum class ExecutionStatus { SUCCEEDED, FAILED, STALE }
enum class ExecutionEffect { ALLOWED, OVERLAY_ATTACHED }
enum class ExecutionFailureReason { HOME_UNCONFIRMED, OVERLAY_ATTACH_FAILED, CAPABILITY_UNAVAILABLE, COMMAND_TIMEOUT }

sealed interface ExecutionOutcome {
    data class Succeeded(
        val effect: ExecutionEffect,
        val homeConfirmed: Boolean = false,
    ) : ExecutionOutcome

    data class Failed(val reason: ExecutionFailureReason) : ExecutionOutcome
    data object Stale : ExecutionOutcome
}

/** A future runtime owner must validate identity and actual effect before committing a stage. */
data class ExecutionReceipt(
    val identity: EvaluationIdentity,
    val kind: ExecutionKind,
    val outcome: ExecutionOutcome,
) {
    val status: ExecutionStatus
        get() = when (outcome) {
            is ExecutionOutcome.Succeeded -> ExecutionStatus.SUCCEEDED
            is ExecutionOutcome.Failed -> ExecutionStatus.FAILED
            ExecutionOutcome.Stale -> ExecutionStatus.STALE
        }
}
