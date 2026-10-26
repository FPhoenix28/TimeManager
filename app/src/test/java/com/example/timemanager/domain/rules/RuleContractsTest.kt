package com.example.timemanager.domain.rules

import org.junit.Assert.*
import org.junit.Test

class RuleContractsTest {
    private val fixture = V090CompatibilityFixtures

    @Test fun dailySelectionRetainsTheSuppressedSessionAndItsReason() {
        val daily = RuleCandidate.Daily(600L, fixture.daily(), DailyCandidateReason.LIMIT_CROSSED)
        val session = RuleCandidate.Session(fixture.session(), SessionCandidateReason.EPISODE_THRESHOLD_REACHED)
        // The test provides the selection; Phase 2 does not select a winner.
        val evaluation = RuleEvaluation(fixture.identity, daily,
            suppressed = listOf(SuppressedCandidate(session, SuppressionReason.DAILY_ACTIVE)))
        assertSame(daily, evaluation.selected)
        assertSame(session, evaluation.suppressed.single().candidate)
        assertEquals(SessionCandidateReason.EPISODE_THRESHOLD_REACHED, session.reason)
    }

    @Test fun ordinaryOwnAndNecessarySystemTargetsAreExpressibleWithoutAndroidObjects() {
        val ordinary = TargetFacts(fixture.PACKAGE, disposition = TargetDisposition.ORDINARY_APP)
        val own = TargetFacts("example.own", disposition = TargetDisposition.OWN_APP)
        val call = TargetFacts("example.calls", disposition = TargetDisposition.NECESSARY_CALL_UI)
        assertEquals(3, listOf(ordinary.disposition, own.disposition, call.disposition).distinct().size)
        val exemption = RuleExemption(RuleKind.DEEP_FOCUS, call.disposition)
        val evaluation = RuleEvaluation(fixture.identity, selected = null, explicitExemptions = listOf(exemption))
        assertEquals(RuleKind.DEEP_FOCUS, evaluation.explicitExemptions.single().rule)
    }

    @Test fun missingAccountingAndRequiredCapabilityDoNotPretendProtectionSucceeded() {
        val context = fixture.context().copy(daily = null)
        val evaluation = RuleEvaluation(context.identity, selected = null,
            insufficientFacts = listOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)),
            requiredCapabilities = listOf(CandidateCapabilityGap(RuleCandidate.Focus(61_000L), RequiredCapability.ACCESSIBILITY_CONNECTION)))
        assertNull(context.daily)
        assertEquals(setOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)), evaluation.insufficientFacts)
        assertEquals(RequiredCapability.ACCESSIBILITY_CONNECTION, evaluation.requiredCapabilities.single().capability)
        assertEquals(RuleKind.FOCUS, evaluation.requiredCapabilities.single().candidate.kind)
    }

    @Test fun unclassifiedTargetCanBeReportedAsInsufficientFactsRatherThanAssumedOrdinary() {
        val target = TargetFacts(fixture.PACKAGE)
        val evaluation = RuleEvaluation(fixture.identity, selected = null,
            insufficientFacts = listOf(RuleFactGap(RuleKind.DEEP_FOCUS, MissingRuleFact.TARGET_CLASSIFICATION)))
        assertEquals(TargetDisposition.UNCLASSIFIED, target.disposition)
        assertEquals(setOf(RuleFactGap(RuleKind.DEEP_FOCUS, MissingRuleFact.TARGET_CLASSIFICATION)), evaluation.insufficientFacts)
        assertTrue(evaluation.explicitExemptions.isEmpty())
    }

    @Test fun executionWindowOwnershipIsSeparateFromRuleFacts() {
        val daily = RuleCandidate.Daily(600L, fixture.daily(), DailyCandidateReason.LIMIT_CROSSED)
        val evaluation = RuleEvaluation(fixture.identity, daily)
        val occupied = ExecutionContextSnapshot(
            overlayOwner = OverlayOwnershipSnapshot(RuleKind.SESSION, fixture.PACKAGE),
            overlayAttached = true,
        )
        val released = occupied.copy(overlayOwner = null, overlayAttached = false)
        assertNotEquals(occupied, released)
        assertSame(daily, evaluation.selected)
        assertEquals(600L, (evaluation.selected as RuleCandidate.Daily).runtime.usageSeconds)
    }

    @Test fun failedAttachReceiptIsNotSuccessfulSessionExecutionOrAStageTransition() {
        val snapshot = fixture.session()
        val candidate = RuleCandidate.Session(snapshot, SessionCandidateReason.EPISODE_THRESHOLD_REACHED)
        val proposal = ExecutionProposal(fixture.identity, ExecutionKind.SHOW_IN_PLACE, candidate)
        val receipt = ExecutionReceipt(proposal.identity, proposal.kind,
            ExecutionOutcome.Failed(ExecutionFailureReason.OVERLAY_ATTACH_FAILED))
        assertEquals(ExecutionStatus.FAILED, receipt.status)
        assertEquals(0, snapshot.episode.reminderCount)
        assertEquals(600L, snapshot.episode.nextReminderAtSeconds)
        assertNull(snapshot.episode.pendingGate)
    }

    @Test fun attachedAllowedAndStaleOutcomesKeepDecisionIdentityAndActualEffect() {
        val attached = ExecutionReceipt(fixture.identity, ExecutionKind.HOME_THEN_SHOW,
            ExecutionOutcome.Succeeded(ExecutionEffect.OVERLAY_ATTACHED, homeConfirmed = true))
        val allowed = ExecutionReceipt(fixture.identity, ExecutionKind.ALLOW,
            ExecutionOutcome.Succeeded(ExecutionEffect.ALLOWED))
        val stale = ExecutionReceipt(fixture.identity, ExecutionKind.SHOW_IN_PLACE, ExecutionOutcome.Stale)
        assertEquals(ExecutionStatus.SUCCEEDED, attached.status)
        assertNotEquals(attached.outcome, allowed.outcome)
        assertEquals(ExecutionStatus.STALE, stale.status)
        assertEquals(fixture.identity, stale.identity)
    }

    @Test fun evaluationOwnsFrozenCopiesOfSuppressedCandidatesAndRequirements() {
        val candidate = RuleCandidate.Session(fixture.session(), SessionCandidateReason.REENTRY_GATE_ELIGIBLE)
        val suppressed = mutableListOf(SuppressedCandidate(candidate, SuppressionReason.DAILY_ACTIVE))
        val requirements = mutableListOf(CandidateCapabilityGap(candidate, RequiredCapability.OVERLAY))
        val constraints = mutableSetOf(RuleKind.DAILY)
        val evaluation = RuleEvaluation(fixture.identity, null, suppressed = suppressed,
            requiredCapabilities = requirements, activeConstraints = constraints)
        suppressed.clear()
        requirements.clear()
        constraints.clear()
        assertSame(candidate, evaluation.suppressed.single().candidate)
        assertEquals(setOf(CandidateCapabilityGap(candidate, RequiredCapability.OVERLAY)), evaluation.requiredCapabilities)
        assertEquals(setOf(RuleKind.DAILY), evaluation.activeConstraints)
        assertThrows(UnsupportedOperationException::class.java) {
            (evaluation.activeConstraints as MutableSet<RuleKind>).clear()
        }
    }
}
