package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.intervention.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.SessionFrictionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class AccessibilityEntryExecutionBoundaryTest {
    private var now = 1_000L
    private val friction = SessionFrictionManager(clock = { now })
    private val coordinator = InterventionCoordinator(friction, "own", wallClock = { 2_000L })

    private fun startEntry(): ForegroundEntry {
        coordinator.setAccessibilityConnected(true, "launcher")
        coordinator.updateManagedPackages(setOf("app"))
        friction.beginPhysicalSession("app", 20L)
        friction.onActualUsage("app", 20L)
        val reminder = friction.beginGate("app", SessionGatePhase.REMINDER)!!
        friction.onReminderShown("app", reminder.id)
        friction.closeGate("app", reminder.id)
        friction.endPhysicalSession("app", 20L)
        coordinator.onForegroundWindow("launcher", true)
        return coordinator.onForegroundWindow("app", true)!!
    }
    private fun input(entry: ForegroundEntry) = accessibilitySnapshot(episode = friction.snapshot("app"))
        .let { it.copy(identity = it.identity.copy(foregroundGeneration = ForegroundGeneration(entry.generation))) }

    @Test fun oldPublicationAndWrongGenerationAreNeverFresh() {
        val entry = startEntry()
        coordinator.publishDaily("app", DailyLimitSnapshot("day", 20L, 60L, false))
        val old = coordinator.readDailyObservation("app")!!
        assertFalse(AccessibilityEntryFreshness.matches(entry, old.sequence, "day", old))
        coordinator.publishDaily("app", DailyLimitSnapshot("day", 20L, 60L, false))
        val fresh = coordinator.readDailyObservation("app")!!
        assertTrue(AccessibilityEntryFreshness.matches(entry, old.sequence, "day", fresh))
        assertFalse(AccessibilityEntryFreshness.matches(entry.copy(generation = entry.generation + 1L), old.sequence, "day", fresh))
        assertFalse(AccessibilityEntryFreshness.matches(entry, old.sequence, "next-day", fresh))
    }
    @Test fun existingFreshCheckIsRunExactlyOnceAndDoesNotConsumeGateOrToken() = runBlocking {
        val entry = startEntry()
        var checks = 0
        val before = friction.snapshot("app")
        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.monitorCommands.collect { command ->
                if (command is MonitorInterventionCommand.CheckEntry) {
                    checks++
                    coordinator.publishDaily("app", DailyLimitSnapshot("day", 20L, 60L, false))
                    command.result.complete(EntryCheckResult.CHECK_FRICTION)
                }
            }
        }
        try {
            assertEquals(EntryCheckResult.CHECK_FRICTION, coordinator.checkEntry(entry))
            repeat(10) { assertEquals(AccessibilityShadowStatus.MATCH,
                AccessibilityRulesShadow().compare(input(entry), AccessibilityIntent.CHECK_SESSION_REENTRY).status) }
            assertEquals(1, checks)
            assertEquals(before, friction.snapshot("app"))
        } finally { listener.cancelAndJoin() }
    }
    @Test fun selectedSessionIsNotHomeConfirmationAndRepeatedEventsDoNotDuplicateExecution() = runBlocking {
        val entry = startEntry()
        val shadow = AccessibilityRulesShadow()
        val before = friction.snapshot("app")
        repeat(20) {
            assertNull(coordinator.onForegroundWindow("app", true))
            assertEquals(AccessibilityShadowStatus.MATCH, shadow.compare(input(entry), AccessibilityIntent.CHECK_SESSION_REENTRY).status)
        }
        assertEquals(before, friction.snapshot("app"))
        var homes = 0
        var overlays = 0
        var gates = 0
        val acknowledged = CompletableDeferred<Unit>()
        val homeListener = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                homes++
                coordinator.markHomeRequested(command.packageName)
                command.result.complete(true)
                acknowledged.complete(Unit)
            }
        }
        try {
            val home = async(start = CoroutineStart.UNDISPATCHED) { coordinator.requestSessionHome("app") }
            acknowledged.await()
            assertFalse(home.isCompleted)
            assertNull(friction.snapshot("app")!!.pendingGate)
            repeat(10) { shadow.compare(input(entry), AccessibilityIntent.CHECK_SESSION_REENTRY) }
            assertEquals(before, friction.snapshot("app"))
            coordinator.onForegroundWindow("launcher", true)
            assertTrue(home.await())
            val gate = friction.beginGate("app", SessionGatePhase.REENTRY)!!
            gates++
            overlays++ // Test execution sink, not an Android WindowManager attachment.
            assertFalse(friction.snapshot("app")!!.reentryGateArmed)
            assertEquals(1, friction.snapshot("app")!!.reminderCount)
            assertEquals(gate, friction.beginGate("app", SessionGatePhase.REENTRY))
            assertEquals(1, homes)
            assertEquals(1, gates)
            assertEquals(1, overlays)
        } finally { homeListener.cancelAndJoin() }
    }
    @Test fun newPhysicalEntryHasNewGenerationButShadowNeverRearmsConsumedStage() {
        val first = startEntry()
        val gate = friction.beginGate("app", SessionGatePhase.REENTRY)!!
        friction.closeGate("app", gate.id)
        coordinator.onForegroundWindow("launcher", true)
        val next = coordinator.onForegroundWindow("app", true)!!
        assertTrue(next.generation > first.generation)
        val before = friction.snapshot("app")
        val result = AccessibilityRulesShadow().compare(input(next), AccessibilityIntent.ALLOW_ENTRY)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(before, friction.snapshot("app"))
        assertFalse(friction.isReentryGateArmed("app"))
    }
    @Test fun managedRemovalOnlyClearsObservationMetadataThroughExistingOwner() {
        startEntry()
        coordinator.publishDaily("app", DailyLimitSnapshot("day", 20L, 60L, false))
        assertTrue(coordinator.isManagedForRules("app"))
        coordinator.updateManagedPackages(emptySet())
        assertFalse(coordinator.isManagedForRules("app"))
        assertNull(coordinator.readDailyObservation("app"))
    }
}
