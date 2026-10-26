package com.example.timemanager.monitor.shadow

import com.example.timemanager.domain.intervention.InterventionCoordinator
import com.example.timemanager.domain.limit.LimitInterventionTriggerState
import com.example.timemanager.monitor.SessionFrictionManager
import org.junit.Assert.*
import org.junit.Test

class LegacyReadAccessorsTest {
    @Test fun repeatedDailyReadsDoNotConsumeAnEntryOrRecordAnOverLimitEdge() {
        val owner = LimitInterventionTriggerState()
        owner.onManagedAppEntered("example.app")
        val first = owner.readSnapshot("example.app", "day")
        assertEquals(first, owner.readSnapshot("example.app", "day"))
        assertFalse(first.previousOverLimit)
        assertTrue(first.enteredThisVisit)
        val evaluated = owner.evaluate("example.app", "day", currentOverLimit = true)
        assertTrue(evaluated.limitCrossed)
        assertTrue(evaluated.enteredWhileOverLimit)
    }

    @Test fun peekingAnotherUsageDayDoesNotResetTheOwnersOldDay() {
        val owner = LimitInterventionTriggerState()
        owner.evaluate("example.app", "old", true)
        assertFalse(owner.readSnapshot("example.app", "new").previousOverLimit)
        assertTrue(owner.readSnapshot("example.app", "old").previousOverLimit)
    }

    @Test fun coordinatorObservationDoesNotEstablishOrAdvanceAForegroundVisit() {
        val coordinator = InterventionCoordinator(SessionFrictionManager(clock = { 1_000L }), "owner", wallClock = { 1_000L })
        assertNull(coordinator.foregroundObservation())
        coordinator.updateManagedPackages(setOf("example.app"))
        val entry = coordinator.onForegroundChanged("example.app")!!
        assertEquals(entry, coordinator.foregroundObservation())
        assertEquals(entry, coordinator.foregroundObservation())
        assertTrue(coordinator.isCurrent(entry))
    }
}
