package com.example.timemanager.domain.rules

import org.junit.Assert.*
import org.junit.Test

class RulePriorityResolverTest {
    private val fixture = V090CompatibilityFixtures
    private val candidates = listOf(
        RuleCandidate.Session(fixture.session(), SessionCandidateReason.EPISODE_THRESHOLD_REACHED),
        RuleCandidate.Daily(600L, fixture.daily(), DailyCandidateReason.LIMIT_CROSSED),
        RuleCandidate.Focus(61_000L),
        RuleCandidate.DeepFocus(61_000L),
    )

    @Test fun explicitPrioritiesDoNotDependOnEnumOrder() {
        assertTrue(RulePriorityResolver.priorityOf(RuleKind.DEEP_FOCUS) > RulePriorityResolver.priorityOf(RuleKind.FOCUS))
        assertTrue(RulePriorityResolver.priorityOf(RuleKind.FOCUS) > RulePriorityResolver.priorityOf(RuleKind.DAILY))
        assertTrue(RulePriorityResolver.priorityOf(RuleKind.DAILY) > RulePriorityResolver.priorityOf(RuleKind.SESSION))
    }

    @Test fun everyNonEmptyCombinationHasSameWinnerRegardlessOfInputOrder() {
        for (mask in 1..15) {
            val subset = candidates.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val expected = subset.last()
            assertEquals("mask=$mask", expected, RulePriorityResolver.select(subset))
            assertEquals(expected, RulePriorityResolver.select(subset.reversed()))
            assertEquals(subset.size - 1, RulePriorityResolver.suppressed(subset).size)
            assertTrue(RulePriorityResolver.suppressed(subset).all {
                it.reason == if (expected.kind == RuleKind.DAILY && it.candidate.kind == RuleKind.SESSION) {
                    SuppressionReason.DAILY_ACTIVE
                } else SuppressionReason.HIGHER_PRIORITY_CANDIDATE
            })
        }
    }

    @Test fun noCandidatesMeansNoSelectionOrSuppression() {
        assertNull(RulePriorityResolver.select(emptyList()))
        assertTrue(RulePriorityResolver.suppressed(emptyList()).isEmpty())
    }
}
