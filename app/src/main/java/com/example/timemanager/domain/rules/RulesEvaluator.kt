package com.example.timemanager.domain.rules

/** Stateless evaluation of supplied facts. Selection never performs or commits an intervention. */
class RulesEvaluator {
    fun evaluate(context: RuleEvaluationContext): RuleEvaluation {
        val focus = FocusCandidateEvaluator.evaluate(context)
        val usage = DailySessionCandidateEvaluator.evaluate(context)
        val candidates = focus.candidates + usage.candidates
        val activeConstraints = focus.activeConstraints + usage.activeConstraints
        return RuleEvaluation(
            identity = context.identity,
            selected = RulePriorityResolver.select(candidates, activeConstraints),
            suppressed = RulePriorityResolver.suppressed(candidates, activeConstraints),
            explicitExemptions = focus.exemptions,
            insufficientFacts = focus.gaps + usage.gaps,
            requiredCapabilities = candidates.flatMap { CandidateCapabilityEvaluator.missing(it, context.capabilities) },
            activeConstraints = activeConstraints,
        )
    }
}
