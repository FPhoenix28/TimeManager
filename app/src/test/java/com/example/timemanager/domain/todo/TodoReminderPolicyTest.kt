package com.example.timemanager.domain.todo

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TodoReminderPolicyTest {
    private fun policy(at: String) = TodoReminderPolicy(Clock.fixed(
        OffsetDateTime.parse(at).toInstant(), ZoneId.of("Asia/Shanghai"),
    ))

    @Test fun schedulesThreeStagesBeforeAfternoon() {
        assertEquals(3, policy("2026-10-11T09:00:00+08:00").plan().size)
    }
    @Test fun lateOpeningNeverCatchesUpOldStages() {
        val plan = policy("2026-10-11T23:30:00+08:00").plan()
        assertEquals(listOf(TodoReminderStage.LATE), plan.map { it.stage })
        assertEquals(0L, plan.single().delayMillis)
    }
    @Test fun beforeFourStillUsesPreviousDay() {
        val p = policy("2026-10-12T01:30:00+08:00")
        assertEquals("2026-10-11", p.plan().single().usageDay)
        assertTrue(p.isApplicable("2026-10-11", TodoReminderStage.LATE))
        assertFalse(p.isApplicable("2026-10-12", TodoReminderStage.LATE))
    }
    @Test fun delayedAfternoonWorkerIsSkippedAtEvening() {
        val p = policy("2026-10-11T20:01:00+08:00")
        assertFalse(p.isApplicable("2026-10-11", TodoReminderStage.AFTERNOON))
        assertTrue(p.isApplicable("2026-10-11", TodoReminderStage.EVENING))
    }
    @Test fun newBoundaryStartsFreshStages() {
        val p = policy("2026-10-12T04:00:00+08:00")
        assertEquals("2026-10-12", p.plan().first().usageDay)
        assertFalse(p.isApplicable("2026-10-11", TodoReminderStage.LATE))
    }
}
