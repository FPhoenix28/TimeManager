package com.example.timemanager.domain.rules

/** Business priority only; neither enum order nor execution availability selects the winner. */
object RulePriorityResolver {
    fun priorityOf(kind: RuleKind): Int = when (kind) {
        RuleKind.DEEP_FOCUS -> 400
        RuleKind.FOCUS -> 300
        RuleKind.DAILY -> 200
        RuleKind.SESSION -> 100
    }

    fun select(
        candidates: Collection<RuleCandidate>,
        activeConstraints: Collection<RuleKind> = emptyList(),
    ): RuleCandidate? {
        val dominant = dominantRule(candidates, activeConstraints)
        return candidates.firstOrNull { it.kind == dominant }
    }

    fun suppressed(
        candidates: Collection<RuleCandidate>,
        activeConstraints: Collection<RuleKind> = emptyList(),
    ): List<SuppressedCandidate> {
        val selected = select(candidates, activeConstraints)
        val dominant = dominantRule(candidates, activeConstraints)
        return candidates.filter { it != selected }.map {
            val reason = if (dominant == RuleKind.DAILY && it.kind == RuleKind.SESSION) {
                SuppressionReason.DAILY_ACTIVE
            } else SuppressionReason.HIGHER_PRIORITY_CANDIDATE
            SuppressedCandidate(it, reason)
        }
    }

    private fun dominantRule(candidates: Collection<RuleCandidate>, activeConstraints: Collection<RuleKind>): RuleKind? =
        (activeConstraints + candidates.map { it.kind }).maxByOrNull(::priorityOf)
}
