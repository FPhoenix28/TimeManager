package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.intervention.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.SessionFrictionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class AccessibilityAuthorityBoundaryTest {
    private var elapsed = 1_000L
    private val friction = SessionFrictionManager(clock = { elapsed })
    private val coordinator = InterventionCoordinator(friction, "own", wallClock = { 2_000L })
    private fun entry(): ForegroundEntry {
        coordinator.setAccessibilityConnected(true, "launcher")
        coordinator.updateManagedPackages(setOf("app"))
        coordinator.updateAccessibilityPolicyRevision(1L)
        coordinator.onForegroundWindow("launcher", true)
        return coordinator.onForegroundWindow("app", true)!!
    }
    private fun prepared(entry: ForegroundEntry, day: String = "day"): PreparedAccessibilityEntry {
        coordinator.publishDaily("app", DailyLimitSnapshot(day, 20L, 60L, false), entry)
        return PreparedAccessibilityEntry(entry, coordinator.readDailyObservation("app")!!, 60L, 1L)
    }
    private fun input(entry: ForegroundEntry) = accessibilitySnapshot(episode = friction.snapshot("app")).let {
        it.copy(identity = it.identity.copy(foregroundGeneration = ForegroundGeneration(entry.generation)))
    }
    private fun arm() {
        friction.beginPhysicalSession("app", 20L)
        friction.onActualUsage("app", 20L)
        val gate = friction.beginGate("app", SessionGatePhase.REMINDER)!!
        friction.onReminderShown("app", gate.id)
        friction.closeGate("app", gate.id)
        friction.endPhysicalSession("app", 20L)
    }
    @Test fun r4ChangedGenerationDiscardsBeforeDispatch() = runBlocking {
        val e = entry(); val facts = prepared(e)
        val decision = AccessibilityRulesAuthority().decide(input(e))
        coordinator.onForegroundWindow("launcher", true)
        var effects = 0
        AccessibilityAuthorityDispatch.execute(decision, { coordinator.preparedEntryIsCurrent(facts, "day") },
            engine = { effects++ }, legacy = { effects++ })
        assertEquals(0, effects)
    }
    @Test fun r5WrongUsageDayCannotBeClaimed() {
        val e = entry(); val facts = prepared(e)
        assertFalse(coordinator.claimPreparedEntry(facts, "next-day"))
    }
    @Test fun changedPolicyCannotBeClaimed() {
        val facts = prepared(entry())
        coordinator.updateAccessibilityPolicyRevision(2L)
        assertFalse(coordinator.claimPreparedEntry(facts, "day"))
    }
    @Test fun replacedPublicationCannotBeClaimed() {
        val e = entry(); val facts = prepared(e)
        prepared(e)
        assertFalse(coordinator.claimPreparedEntry(facts, "day"))
    }
    @Test fun staleDiscardDoesNotLeaveTimingPendingOrAdmitOrConsumeStage() {
        val e = entry(); arm(); val facts = prepared(e)
        val before = friction.snapshot("app")
        assertFalse(coordinator.canTime("app"))
        coordinator.updateAccessibilityPolicyRevision(2L)
        assertFalse(coordinator.claimPreparedEntry(facts, "day"))
        coordinator.discardEntryPreparation(e)
        assertTrue(coordinator.canTime("app"))
        assertEquals(before, friction.snapshot("app"))
        assertFalse(friction.isEntryAdmitted("app"))
    }
    @Test fun r6FactsPreparationOneFreshReadAndNoBusinessEffects() = runBlocking {
        val e = entry(); arm()
        val before = friction.snapshot("app")
        var reads = 0
        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.monitorCommands.collect { command ->
                if (command is MonitorInterventionCommand.PrepareEntry) {
                    reads++
                    command.result.complete(prepared(command.entry))
                } else fail("Preparation emitted business command $command")
            }
        }
        try {
            val previous = coordinator.readDailyObservation("app")?.sequence ?: 0L
            val facts = coordinator.prepareEntryFacts(e, 1L)!!
            assertTrue(AccessibilityEntryFreshness.matches(e, previous, "day", facts.publication))
            repeat(20) { AccessibilityRulesAuthority().decide(input(e)) }
            assertEquals(1, reads)
            assertEquals(before, friction.snapshot("app"))
        } finally { listener.cancelAndJoin() }
    }
    @Test fun r12RepeatedGenerationExecutesOnlyOnce() = runBlocking {
        val e = entry(); arm(); val facts = prepared(e)
        val decision = AccessibilityRulesAuthority().decide(input(e))
        var requests = 0
        repeat(20) {
            AccessibilityAuthorityDispatch.execute(decision, { coordinator.preparedEntryIsCurrent(facts, "day") },
                engine = { if (coordinator.claimPreparedEntry(facts, "day")) requests++ }, legacy = { fail("legacy") })
        }
        assertEquals(1, requests)
        assertEquals(1, friction.snapshot("app")!!.reminderCount)
    }
    @Test fun r13NewGenerationMayExecuteOnce() {
        val first = prepared(entry())
        assertTrue(coordinator.claimPreparedEntry(first, "day"))
        coordinator.onForegroundWindow("launcher", true)
        val next = prepared(coordinator.onForegroundWindow("app", true)!!)
        assertTrue(coordinator.claimPreparedEntry(next, "day"))
        assertFalse(coordinator.claimPreparedEntry(next, "day"))
    }
    @Test fun adapterExceptionFallsBackBeforeAnyEngineEffect() = fallbackTest(
        AccessibilityRulesAuthority(adapt = { error("adapter") }))
    @Test fun evaluatorExceptionFallsBackBeforeAnyEngineEffect() = fallbackTest(
        AccessibilityRulesAuthority(evaluate = { error("evaluator") }))
    @Test fun missingCriticalFactsFallsBackBeforeAnyEngineEffect() = fallbackTest(
        AccessibilityRulesAuthority(), accessibilitySnapshot().let { it.copy(policy = it.policy.copy(loaded = false)) })
    private fun fallbackTest(authority: AccessibilityRulesAuthority, input: AccessibilityRulesSnapshot = accessibilitySnapshot()) = runBlocking {
        var engineEffects = 0; var legacyEffects = 0
        AccessibilityAuthorityDispatch.execute(authority.decide(input), { true }, engine = { engineEffects++ }, legacy = { legacyEffects++ })
        assertEquals(0, engineEffects); assertEquals(1, legacyEffects)
    }
    @Test fun engineExecutionExceptionNeverTriggersLegacy() = runBlocking {
        var engine = 0; var legacy = 0
        try {
            AccessibilityAuthorityDispatch.execute(AccessibilityRulesAuthority().decide(accessibilitySnapshot()), { true },
                engine = { engine++; error("execution") }, legacy = { legacy++ })
            fail("Must propagate to execution fail-open, not business fallback")
        } catch (_: IllegalStateException) { }
        assertEquals(1, engine); assertEquals(0, legacy)
    }
    @Test fun deliberateReverseMismatchDoesNotSelectLegacy() = runBlocking {
        val plan = AccessibilityRulesAuthority().decide(accessibilitySnapshot()) as AccessibilityAuthorityDecision.Engine
        assertEquals(AccessibilityShadowStatus.MISMATCH, AccessibilityReverseShadow.compare(plan, AccessibilityIntent.ALLOW_ENTRY).status)
        var engine = 0; var legacy = 0
        AccessibilityAuthorityDispatch.execute(plan, { true }, engine = { engine++ }, legacy = { legacy++ })
        assertEquals(1, engine); assertEquals(0, legacy)
    }
    @Test fun expiredIdentityFromEvaluatorIsDiscardedNotFallback() = runBlocking {
        val authority = AccessibilityRulesAuthority(evaluate = { context -> RulesEvaluator().evaluate(context).let {
            RuleEvaluation(it.identity.copy(policyRevision = PolicyRevision(99L)), it.selected,
                it.suppressed, it.explicitExemptions, it.insufficientFacts, it.requiredCapabilities, it.activeConstraints)
        } })
        var effects = 0
        AccessibilityAuthorityDispatch.execute(authority.decide(accessibilitySnapshot()), { true },
            engine = { effects++ }, legacy = { effects++ })
        assertEquals(0, effects)
    }
    @Test fun essentialDeepHandoffSurvivesLauncherEventWithoutGlobalBlock() {
        entry()
        val deep = activeFocus(com.example.timemanager.data.model.FocusModeType.DEEP_FOCUS)
        coordinator.updateFocus(deep)
        coordinator.publishAccessibilityFocusSelection("app", deep, false)
        coordinator.onForegroundWindow("launcher", true)
        coordinator.publishAccessibilityFocusSelection("launcher", deep, false)
        assertFalse(coordinator.isFocusBlocking("app"))
        assertTrue(coordinator.claimOverlay(InterventionPriority.DAILY, "app"))
        coordinator.releaseOverlay(InterventionPriority.DAILY, "app")
        coordinator.publishAccessibilityFocusSelection("app", deep, true)
        assertTrue(coordinator.isFocusBlocking("app"))
        assertFalse(coordinator.claimOverlay(InterventionPriority.DAILY, "app"))
    }
    @Test fun liveBypassIsConsumedOnlyByOneAdmittedExecution() = runBlocking {
        val e = entry()
        friction.beginPhysicalSession("app", 20L)
        friction.onActualUsage("app", 20L)
        val reminder = friction.beginGate("app", SessionGatePhase.REMINDER)!!
        friction.onReminderShown("app", reminder.id)
        elapsed = reminder.endTimeMillis
        assertTrue(friction.allowNextEntry("app", reminder.id))
        val facts = prepared(e)
        val snapshot = input(e).copy(time = EvaluationTime(2_000L, elapsed))
        val before = friction.snapshot("app")
        val decision = AccessibilityRulesAuthority().decide(snapshot) as AccessibilityAuthorityDecision.Engine
        repeat(10) { AccessibilityRulesAuthority().decide(snapshot) }
        assertEquals(before, friction.snapshot("app"))
        var admissions = 0
        repeat(10) {
            AccessibilityAuthorityDispatch.execute(decision, { coordinator.preparedEntryIsCurrent(facts, "day") }, engine = {
                if (coordinator.claimPreparedEntry(facts, "day")) {
                    assertTrue(friction.consumeBypass("app"))
                    if (coordinator.allowEntry(e)) admissions++
                }
            }, legacy = { fail("legacy") })
        }
        assertEquals(1, admissions)
        assertNull(friction.snapshot("app")!!.bypassExpiresAtMillis)
        assertTrue(friction.isEntryAdmitted("app"))
        assertEquals(1, friction.snapshot("app")!!.reminderCount)
    }
    @Test fun deepEssentialDisagreementIsExpectedAndEngineRetainsLowerAuthority() {
        val essentials = com.example.timemanager.domain.focus.DeepFocusEssentialApps(
            "own", "launcher", "ime", "phone", setOf("phone"), "settings", setOf("systemui"))
        val input = accessibilitySnapshot(activeFocus(com.example.timemanager.data.model.FocusModeType.DEEP_FOCUS),
            TargetDisposition.RECOVERY_SETTINGS_UI, daily = DailyLimitSnapshot("day", 60L, 60L, true))
            .copy(target = TargetFacts("app", "AccessibilitySettings", TargetDisposition.RECOVERY_SETTINGS_UI))
        // Simulated classifier disposition is authoritative; legacy resolver sees the matching actual package.
        val legacyEssentials = essentials.copy(settingsPackage = "app")
        val legacy = AccessibilityLegacyIntent.forEntry(input, legacyEssentials)
        val plan = AccessibilityRulesAuthority().decide(input) as AccessibilityAuthorityDecision.Engine
        assertEquals(AccessibilityIntent.ALLOW_ENTRY, legacy)
        assertEquals(AccessibilityIntent.SUPPRESS_SESSION_DAILY, plan.projection.intent)
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
            AccessibilityReverseShadow.compare(plan, legacy).status)
    }
    @Test fun selectedSessionStillWaitsForActualLauncherBeforeGateAndOverlay() = runBlocking {
        val e = entry(); arm(); val facts = prepared(e)
        val decision = AccessibilityRulesAuthority().decide(input(e))
        val acknowledged = CompletableDeferred<Unit>()
        var homes = 0; var overlays = 0; var gates = 0
        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                homes++; coordinator.markHomeRequested("app")
                command.result.complete(true); acknowledged.complete(Unit)
            }
        }
        try {
            val execution = async(start = CoroutineStart.UNDISPATCHED) {
                AccessibilityAuthorityDispatch.execute(decision, { coordinator.claimPreparedEntry(facts, "day") }, engine = {
                    if (coordinator.requestSessionHome("app")) {
                        val gate = friction.beginGate("app", SessionGatePhase.REENTRY)!!
                        gates++; overlays++
                        friction.closeGate("app", gate.id)
                    }
                }, legacy = { fail("legacy") })
            }
            acknowledged.await()
            assertFalse(execution.isCompleted)
            assertNull(friction.snapshot("app")!!.pendingGate)
            assertEquals(0, overlays)
            repeat(10) { assertNull(coordinator.onForegroundWindow("app", true)) }
            coordinator.onForegroundWindow("launcher", true)
            execution.await()
            assertEquals(1, homes); assertEquals(1, gates); assertEquals(1, overlays)
            assertFalse(friction.isEntryAdmitted("app")); assertEquals(1, friction.snapshot("app")!!.reminderCount)
            assertFalse(friction.isReentryGateArmed("app"))
        } finally { listener.cancelAndJoin() }
    }
}
