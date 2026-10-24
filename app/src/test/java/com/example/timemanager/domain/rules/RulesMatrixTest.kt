package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.domain.profile.DailyRuleConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RulesMatrixTest(private val mask: Int) {
    @Test fun allRuleCombinationsKeepEveryMatchedCandidateAndSelectExplicitPriority() {
        val f = V090CompatibilityFixtures
        val base = f.context()
        val daily = mask and 2 != 0
        val session = mask and 1 != 0
        val focus = FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE))
        val deep = FocusRuntimeSnapshot.DeepFocus(1L, 1_900_000_000_000L, FocusModeStatus.ACTIVE)
        val focusFacts = when {
            mask and 12 == 12 -> FocusRuntimeSnapshot.Concurrent(focus, deep)
            mask and 8 != 0 -> deep
            mask and 4 != 0 -> focus
            else -> FocusRuntimeSnapshot.Off
        }
        val context = base.copy(
            effectivePolicy = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(
                daily = if (daily) DailyRuleConfig.Explicit(3_600L) else DailyRuleConfig.Unset)),
            daily = if (daily) f.daily(3_600L) else null,
            session = if (session) f.session() else null,
            focus = focusFacts,
        )
        val expected = when {
            mask and 8 != 0 -> RuleKind.DEEP_FOCUS
            mask and 4 != 0 -> RuleKind.FOCUS
            daily -> RuleKind.DAILY
            session -> RuleKind.SESSION
            else -> null
        }
        val before = context.copy()
        val result = RulesEvaluator().evaluate(context)
        assertEquals(expected, result.selected?.kind)
        val matchedKinds = buildSet {
            if (daily) add(RuleKind.DAILY)
            if (session) add(RuleKind.SESSION)
            if (mask and 4 != 0) add(RuleKind.FOCUS)
            if (mask and 8 != 0) add(RuleKind.DEEP_FOCUS)
        }
        assertEquals(matchedKinds, (listOfNotNull(result.selected) + result.suppressed.map { it.candidate }).map { it.kind }.toSet())
        assertEquals(matchedKinds, result.activeConstraints)
        assertEquals(before, context)
        assertEquals(base.identity, result.identity)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "rule combination {0}")
        fun cases(): List<Array<Int>> = (0..15).map { arrayOf(it) }
    }
}
