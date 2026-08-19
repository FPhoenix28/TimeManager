package com.example.timemanager.domain.limit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitInterventionTriggerStateTest {
    private val evaluator = UsageLimitEvaluator()
    private val triggerState = LimitInterventionTriggerState()

    @Test
    fun `crossing the limit in the current session triggers at the exact second`() {
        val packageName = "example.app"

        val at3598 = evaluate(packageName, 3_598L)
        val at3599 = evaluate(packageName, 3_599L)
        val at3600 = evaluate(packageName, 3_600L)
        val at3601 = evaluate(packageName, 3_601L)

        assertFalse(at3598.limitCrossed)
        assertFalse(at3599.limitCrossed)
        assertTrue(at3600.limitCrossed)
        assertFalse(at3601.limitCrossed)
        assertTrue(at3600.currentOverLimit)
    }

    @Test
    fun `entering an already over-limit app triggers on the first evaluation`() {
        val packageName = "example.app"
        evaluate(packageName, 3_900L)
        triggerState.onAppLeft(packageName)
        triggerState.onManagedAppEntered(packageName)

        val entered = evaluate(packageName, 3_901L)
        val nextTick = evaluate(packageName, 3_902L)

        assertFalse(entered.limitCrossed)
        assertTrue(entered.enteredWhileOverLimit)
        assertFalse(nextTick.enteredWhileOverLimit)
    }

    @Test
    fun `entry remains pending until limit data is evaluated`() {
        val packageName = "example.app"
        triggerState.onManagedAppEntered(packageName)

        val delayedFirstEvaluation = evaluate(packageName, 3_900L)

        assertTrue(delayedFirstEvaluation.enteredWhileOverLimit)
    }

    @Test
    fun `extension resets over-limit state and crosses again at effective limit`() {
        val packageName = "example.app"
        val initialLimit = evaluator.evaluate(packageName, 3_600L, 60)
        triggerState.evaluate(packageName, USAGE_DAY, initialLimit.isLimitReached)

        triggerState.onLimitExtended(packageName, USAGE_DAY)
        val beforeExtendedLimit = evaluator.evaluate(packageName, 3_899L, 65)
        val beforeSnapshot = triggerState.evaluate(
            packageName,
            USAGE_DAY,
            beforeExtendedLimit.isLimitReached,
        )
        val atExtendedLimit = evaluator.evaluate(packageName, 3_900L, 65)
        val atSnapshot = triggerState.evaluate(
            packageName,
            USAGE_DAY,
            atExtendedLimit.isLimitReached,
        )

        assertFalse(beforeSnapshot.currentOverLimit)
        assertFalse(beforeSnapshot.limitCrossed)
        assertTrue(atSnapshot.currentOverLimit)
        assertTrue(atSnapshot.limitCrossed)
    }

    private fun evaluate(
        packageName: String,
        usedSeconds: Long,
    ): LimitInterventionTriggerState.Snapshot {
        val evaluation = evaluator.evaluate(packageName, usedSeconds, 60)
        return triggerState.evaluate(
            packageName = packageName,
            usageDayKey = USAGE_DAY,
            currentOverLimit = evaluation.isLimitReached,
        )
    }

    private companion object {
        const val USAGE_DAY = "2026-08-19"
    }
}
