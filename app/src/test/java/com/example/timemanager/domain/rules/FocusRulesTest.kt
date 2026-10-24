package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import org.junit.Assert.*
import org.junit.Test

class FocusRulesTest {
    private val fixture = V090CompatibilityFixtures
    private val evaluator = RulesEvaluator()
    private fun context(focus: FocusRuntimeSnapshot) = fixture.context().copy(
        focus = focus, daily = null, session = null,
        effectivePolicy = fixture.context().effectivePolicy.let { it.copy(appPolicy = it.appPolicy.copy(managed = false)) },
        time = EvaluationTime(10_000L, 1_000L),
    )
    private fun focus(status: FocusModeStatus = FocusModeStatus.ACTIVE) =
        FocusRuntimeSnapshot.Focus(1_000L, 61_000L, setOf(fixture.PACKAGE), status)
    private fun deep(status: FocusModeStatus = FocusModeStatus.ACTIVE) =
        FocusRuntimeSnapshot.DeepFocus(1_000L, 61_000L, status)

    @Test fun ordinaryTargetProducesDeepCandidate() {
        assertEquals(RuleKind.DEEP_FOCUS, evaluator.evaluate(context(deep())).selected?.kind)
    }

    @Test fun selectedFocusAppNeedNotBeManaged() {
        val context = context(focus())
        assertFalse(context.effectivePolicy.appPolicy.managed)
        assertEquals(RuleKind.FOCUS, evaluator.evaluate(context).selected?.kind)
    }

    @Test fun offProducesNoFocusCandidates() {
        assertNull(evaluator.evaluate(context(FocusRuntimeSnapshot.Off)).selected)
    }

    @Test fun focusOwnAppIsNotBlockedEvenWhenSelected() {
        val context = context(focus())
        assertNull(evaluator.evaluate(context.copy(target = context.target.copy(disposition = TargetDisposition.OWN_APP))).selected)
    }

    @Test fun unselectedFocusTargetIsNotBlocked() {
        assertNull(evaluator.evaluate(context(FocusRuntimeSnapshot.Focus(1_000L, 61_000L, setOf("other")))).selected)
    }

    @Test fun focusWindowIsStartInclusiveAndEndExclusive() {
        val context = context(focus())
        for ((now, expected) in listOf(999L to false, 1_000L to true, 60_999L to true, 61_000L to false)) {
            assertEquals("now=$now", expected, evaluator.evaluate(context.copy(time = context.time.copy(wallNowMillis = now))).selected != null)
        }
    }

    @Test fun completedFocusDoesNotBlock() {
        assertNull(evaluator.evaluate(context(focus(FocusModeStatus.COMPLETED))).selected)
    }

    @Test fun deepWindowAndCompletedStatusStopBlocking() {
        val context = context(deep())
        for (now in listOf(999L, 61_000L)) {
            assertNull(evaluator.evaluate(context.copy(time = context.time.copy(wallNowMillis = now))).selected)
        }
        assertNull(evaluator.evaluate(context(deep(FocusModeStatus.COMPLETED))).selected)
    }

    @Test fun allEssentialDispositionsAreDeepScopedExemptions() {
        val base = context(deep())
        for (disposition in TargetDisposition.entries.filter { it != TargetDisposition.ORDINARY_APP && it != TargetDisposition.UNCLASSIFIED }) {
            val result = evaluator.evaluate(base.copy(target = base.target.copy(disposition = disposition)))
            assertNull(result.selected)
            assertEquals(listOf(RuleExemption(RuleKind.DEEP_FOCUS, disposition)), result.explicitExemptions)
            assertTrue(result.insufficientFacts.isEmpty())
        }
    }

    @Test fun unknownDeepTargetIsMissingClassificationNotOrdinaryOrExempt() {
        val base = context(deep())
        val result = evaluator.evaluate(base.copy(target = base.target.copy(disposition = TargetDisposition.UNCLASSIFIED)))
        assertNull(result.selected)
        assertEquals(setOf(RuleFactGap(RuleKind.DEEP_FOCUS, MissingRuleFact.TARGET_CLASSIFICATION)), result.insufficientFacts)
        assertTrue(result.explicitExemptions.isEmpty())
    }

    @Test fun deepExemptionStillEvaluatesIndependentFocusFact() {
        val base = context(FocusRuntimeSnapshot.Concurrent(focus(), deep()))
        val result = evaluator.evaluate(base.copy(target = base.target.copy(disposition = TargetDisposition.NECESSARY_CALL_UI)))
        assertEquals(RuleKind.FOCUS, result.selected?.kind)
        assertEquals(RuleKind.DEEP_FOCUS, result.explicitExemptions.single().rule)
    }

    @Test fun concurrentDeepAndFocusFactsRetainSuppressedFocus() {
        val result = evaluator.evaluate(context(FocusRuntimeSnapshot.Concurrent(focus(), deep())))
        assertEquals(RuleKind.DEEP_FOCUS, result.selected?.kind)
        assertEquals(RuleKind.FOCUS, result.suppressed.single().candidate.kind)
    }
}
