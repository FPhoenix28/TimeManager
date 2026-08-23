package com.example.timemanager.domain.limit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLimitEvaluatorTest {
    private val evaluator = UsageLimitEvaluator()

    @Test
    fun warningStartsAtExactlyEightyPercent() {
        val before = evaluator.evaluate("example.app", 2_879L, 60)
        val atThreshold = evaluator.evaluate("example.app", 2_880L, 60)

        assertFalse(before.hasReachedWarning)
        assertTrue(atThreshold.hasReachedWarning)
        assertFalse(atThreshold.isLimitReached)
        assertEquals(720L, atThreshold.remainingSeconds)
        assertEquals(0.8f, atThreshold.progress)
    }

    @Test
    fun limitIsReachedAtExactlyOneHundredPercent() {
        val result = evaluator.evaluate("example.app", 3_600L, 60)

        assertTrue(result.hasReachedWarning)
        assertTrue(result.isLimitReached)
        assertEquals(0L, result.remainingSeconds)
        assertEquals(1f, result.progress)
    }

    @Test
    fun secondBasedLimitUsesTheSameEvaluationLogic() {
        val before = evaluator.evaluateSeconds("example.app", 59L, 60L)
        val reached = evaluator.evaluateSeconds("example.app", 60L, 60L)

        assertFalse(before.isLimitReached)
        assertTrue(reached.isLimitReached)
        assertEquals(60L, reached.limitSeconds)
    }

    @Test
    fun savedThirtyMinuteLimitUsesEighteenHundredSeconds() {
        val before = evaluator.evaluate("example.app", 1_799L, 30)
        val reached = evaluator.evaluate("example.app", 1_800L, 30)

        assertFalse(before.isLimitReached)
        assertTrue(reached.isLimitReached)
        assertEquals(1_800L, reached.limitSeconds)
    }
}
