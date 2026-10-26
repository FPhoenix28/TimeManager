package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.domain.focus.FocusEnforcementPolicy

internal data class CandidateFacts(
    val candidates: List<RuleCandidate> = emptyList(),
    val exemptions: List<RuleExemption> = emptyList(),
    val gaps: List<RuleFactGap> = emptyList(),
    val activeConstraints: Set<RuleKind> = candidates.map { it.kind }.toSet(),
)

internal object FocusCandidateEvaluator {
    fun evaluate(context: RuleEvaluationContext): CandidateFacts = when (val focus = context.focus) {
        FocusRuntimeSnapshot.Off -> CandidateFacts()
        is FocusRuntimeSnapshot.Focus -> ordinary(context, focus)
        is FocusRuntimeSnapshot.DeepFocus -> deep(context, focus)
        is FocusRuntimeSnapshot.Concurrent -> {
            val ordinary = ordinary(context, focus.focus)
            val deep = deep(context, focus.deepFocus)
            CandidateFacts(deep.candidates + ordinary.candidates, deep.exemptions, deep.gaps)
        }
    }

    private fun ordinary(context: RuleEvaluationContext, focus: FocusRuntimeSnapshot.Focus): CandidateFacts {
        val state = FocusModeState(FocusModeType.FOCUS, focus.status,
            focus.startWallMillis, focus.endWallMillis, focus.blockedPackages)
        // TargetFacts already supplies own-app classification. Empty cannot equal a valid target.
        val ownPackage = if (context.target.disposition == TargetDisposition.OWN_APP) context.target.packageName else ""
        val matched = FocusEnforcementPolicy.shouldBlock(state, context.target.packageName,
            ownPackage, context.time.wallNowMillis)
        return CandidateFacts(candidates = if (matched) listOf(RuleCandidate.Focus(focus.endWallMillis)) else emptyList())
    }

    private fun deep(context: RuleEvaluationContext, focus: FocusRuntimeSnapshot.DeepFocus): CandidateFacts {
        val state = FocusModeState(FocusModeType.DEEP_FOCUS, focus.status,
            focus.startWallMillis, focus.endWallMillis)
        if (!state.isDeepFocusActive(context.time.wallNowMillis)) return CandidateFacts()
        // OS package/class resolution stays outside the engine; no duplicate call-UI heuristics.
        return when (val disposition = context.target.disposition) {
            TargetDisposition.UNCLASSIFIED -> CandidateFacts(gaps = listOf(
                RuleFactGap(RuleKind.DEEP_FOCUS, MissingRuleFact.TARGET_CLASSIFICATION)))
            TargetDisposition.ORDINARY_APP -> CandidateFacts(candidates = listOf(
                RuleCandidate.DeepFocus(focus.endWallMillis)))
            else -> CandidateFacts(exemptions = listOf(RuleExemption(RuleKind.DEEP_FOCUS, disposition)))
        }
    }
}
