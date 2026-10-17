package com.example.timemanager.domain.intervention

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.service.selectInterventionType
import com.example.timemanager.monitor.SessionFrictionManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class InterventionCoordinatorTest {
    private var now = 1_000L
    private val friction = SessionFrictionManager(clock = { now })
    private val coordinator = InterventionCoordinator(friction, "own", wallClock = { now })

    @Test fun reentryDailyQuotaWinsOverFrictionAndUnconsumedBypass() {
        activateFriction()
        val gate = friction.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        friction.allowNextEntry("one", gate.id)
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 3_600L, 3_600L, isOverLimit = true))
        assertEquals(InterventionPriority.DAILY, coordinator.entryPriority("one"))
        assertNotNull(friction.snapshot("one")!!.bypassExpiresAtMillis)
        assertEquals(1, friction.snapshot("one")!!.reminderCount)
    }

    @Test fun dailyExtensionDoesNotResetEpisodeOrFriction() {
        activateFriction()
        val before = friction.snapshot("one")!!
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 3_600L, 4_500L, isOverLimit = false))
        assertEquals(InterventionPriority.SESSION, coordinator.entryPriority("one"))
        assertEquals(before, friction.snapshot("one"))
    }

    @Test fun focusAndDeepPrecedeDailyButCompletedDeepDoesNotEnforce() {
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 3_600L, 3_600L, isOverLimit = true))
        val focus = FocusModeState(FocusModeType.FOCUS, startTimeMillis = 500L,
            endTimeMillis = 10_000L, blockedPackages = setOf("one"))
        coordinator.updateFocus(focus)
        assertEquals(InterventionPriority.FOCUS, coordinator.entryPriority("one"))
        coordinator.updateFocus(focus.copy(mode = FocusModeType.DEEP_FOCUS))
        assertEquals(InterventionPriority.DEEP, coordinator.entryPriority("one"))
        coordinator.updateFocus(focus.copy(mode = FocusModeType.DEEP_FOCUS, status = FocusModeStatus.COMPLETED))
        assertFalse(coordinator.isFocusBlocking("one"))
        assertEquals(InterventionPriority.DAILY, coordinator.entryPriority("one"))
    }

    @Test fun homeLauncherOverlayEchoAndReentryDoNotCreateHomeLoop() {
        coordinator.updateManagedPackages(setOf("one"))
        val entry = coordinator.onForegroundChanged("one")!!
        assertTrue(coordinator.isCurrent(entry))
        coordinator.onHomeSucceeded("one")
        assertTrue(coordinator.shouldIgnoreHomeEcho("one"))
        assertNull(coordinator.onForegroundChanged("one"))
        coordinator.onForegroundChanged("launcher")
        assertFalse(coordinator.shouldIgnoreHomeEcho("one"))
        val reentry = coordinator.onForegroundChanged("one")!!
        assertFalse(coordinator.isCurrent(entry))
        assertTrue(coordinator.isCurrent(reentry))
        assertNull(coordinator.onForegroundChanged("one"))
    }

    @Test fun transientWindowCannotConfirmHomeExitOrTurnInternalBackIntoReentry() {
        coordinator.updateManagedPackages(setOf("one"))
        val first = coordinator.onForegroundWindow("one", stableAppWindow = true)!!
        coordinator.allowEntry(first)
        assertNull(coordinator.onForegroundWindow("dialog-owner", stableAppWindow = false))
        assertNull(coordinator.onForegroundWindow("one", stableAppWindow = true))
        assertEquals("one", coordinator.stableForegroundPackage())
        coordinator.onHomeSucceeded("one")
        assertTrue(coordinator.awaitingStableExit("one"))
        assertNull(coordinator.onForegroundWindow("dialog-owner", stableAppWindow = false))
        assertNull(coordinator.onForegroundWindow("one", stableAppWindow = true))
        assertTrue(coordinator.awaitingStableExit("one"))
        activateFriction()
        val gate = friction.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        friction.allowNextEntry("one", gate.id)
        assertNull(coordinator.onForegroundWindow("one", stableAppWindow = true))
        assertTrue(coordinator.awaitingStableExit("one"))
        coordinator.onForegroundWindow("launcher", stableAppWindow = true)
        assertFalse(coordinator.awaitingStableExit("one"))
        assertNotNull(coordinator.onForegroundWindow("one", stableAppWindow = true))
    }

    @Test fun dailyPriorityLeavesStageGateArmedUntilItActuallyRuns() {
        activateFriction()
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 3_600L, 3_600L, isOverLimit = true))
        assertEquals(InterventionPriority.DAILY, coordinator.entryPriority("one"))
        assertTrue(friction.isReentryGateArmed("one"))
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 3_600L, 4_500L, isOverLimit = false))
        assertEquals(InterventionPriority.SESSION, coordinator.entryPriority("one"))
        val gate = friction.beginGate("one", SessionGatePhase.REENTRY)!!
        friction.closeGate("one", gate.id)
        assertNull(coordinator.entryPriority("one"))
    }

    @Test fun allowedForegroundVisitSurvivesDuplicateWindowsButEndsOnRealExit() {
        activateFriction()
        coordinator.updateManagedPackages(setOf("one"))
        coordinator.setAccessibilityConnected(true)
        coordinator.onForegroundChanged("launcher")
        val gate = friction.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        friction.allowNextEntry("one", gate.id)
        val entry = coordinator.onForegroundChanged("one")!!
        assertTrue(friction.consumeBypass("one"))
        assertTrue(coordinator.allowEntry(entry))
        assertTrue(coordinator.canTime("one"))
        assertNull(coordinator.onForegroundChanged("one"))
        assertTrue(friction.isEntryAdmitted("one"))
        coordinator.onForegroundChanged("launcher")
        assertFalse(friction.isEntryAdmitted("one"))
        assertTrue(friction.snapshot("one")!!.isActive)
    }

    @Test fun singleOverlayRequiresAcknowledgedReleaseBeforeHigherOwner() {
        assertTrue(coordinator.claimOverlay(InterventionPriority.SESSION, "one"))
        assertFalse(coordinator.claimOverlay(InterventionPriority.DAILY, "one"))
        coordinator.releaseOverlay(InterventionPriority.SESSION, "one")
        assertTrue(coordinator.claimOverlay(InterventionPriority.DAILY, "one"))
        assertFalse(coordinator.claimOverlay(InterventionPriority.FOCUS, "own"))
        coordinator.releaseOverlay(InterventionPriority.DAILY, "one")
        assertTrue(coordinator.claimOverlay(InterventionPriority.FOCUS, "own"))
        assertFalse(coordinator.claimOverlay(InterventionPriority.SESSION, "other"))
    }

    @Test fun noAccessibilityAndUnknownForegroundRemainFailOpenForTiming() {
        assertTrue(coordinator.canTime("one"))
        coordinator.setAccessibilityConnected(true)
        assertTrue(coordinator.canTime("one"))
        coordinator.onForegroundChanged("launcher")
        assertFalse(coordinator.canTime("one"))
        coordinator.setAccessibilityConnected(false)
        assertTrue(coordinator.canTime("one"))
    }

    @Test fun failedAttemptIsAllowedForThatVisitNotAllFutureVisits() {
        coordinator.updateManagedPackages(setOf("one"))
        coordinator.setAccessibilityConnected(true)
        val entry = coordinator.onForegroundChanged("one")!!
        coordinator.failOpenVisit(entry)
        assertTrue(coordinator.canTime("one"))
        assertFalse(coordinator.interventionsAllowed("one"))
        coordinator.onForegroundChanged("launcher")
        coordinator.onForegroundChanged("one")
        assertTrue(coordinator.interventionsAllowed("one"))
    }

    @Test fun disconnectedMessageEndpointsFailOpenWithoutRequestingHome() = runBlocking {
        coordinator.updateManagedPackages(setOf("one"))
        val entry = coordinator.onForegroundChanged("one")!!
        assertEquals(EntryCheckResult.UNAVAILABLE, coordinator.checkEntry(entry))
        assertFalse(coordinator.requestHome("one"))
        coordinator.failOpenVisit(entry)
        assertTrue(coordinator.canTime("one"))
    }

    @Test fun lifecycleCollectorsAcknowledgeDailyEntryAndActualHomeSeparately() = runBlocking {
        coordinator.updateManagedPackages(setOf("one"))
        coordinator.setAccessibilityConnected(true)
        val monitorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.monitorCommands.collect { command ->
                if (command is MonitorInterventionCommand.CheckEntry) {
                    coordinator.publishDaily(command.entry.packageName,
                        DailyLimitSnapshot("day", 600L, 3_600L, isOverLimit = false))
                    command.result.complete(EntryCheckResult.CHECK_FRICTION)
                }
            }
        }
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                assertTrue(coordinator.isObservedForeground(command.packageName))
                coordinator.onHomeSucceeded(command.packageName)
                command.result.complete(true)
            }
        }
        val entry = coordinator.onForegroundChanged("one")!!
        assertFalse(coordinator.canTime("one"))
        assertEquals(EntryCheckResult.CHECK_FRICTION, coordinator.checkEntry(entry))
        assertTrue(coordinator.allowEntry(entry))
        assertTrue(coordinator.canTime("one"))
        assertTrue(coordinator.requestHome("one"))
        assertFalse(coordinator.canTime("one"))
        assertFalse(coordinator.isCurrent(entry))
        monitorJob.cancelAndJoin()
        homeJob.cancelAndJoin()
    }

    @Test fun sessionHomeAcknowledgementWaitsForRealLauncherNotOverlayOrOtherApp() = runBlocking {
        coordinator.updateManagedPackages(setOf("one"))
        coordinator.setAccessibilityConnected(true, launcherPackage = "launcher")
        coordinator.onForegroundWindow("one", stableAppWindow = true)
        val acknowledged = CompletableDeferred<Unit>()
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                assertTrue(command.confirmLauncher)
                coordinator.markHomeRequested(command.packageName)
                command.result.complete(true)
                acknowledged.complete(Unit)
            }
        }
        try {
            val navigation = async(start = CoroutineStart.UNDISPATCHED) { coordinator.requestSessionHome("one") }
            acknowledged.await()
            yield()
            assertFalse("HOME dispatch alone must not complete navigation", navigation.isCompleted)
            assertTrue(coordinator.isObservedForeground("one"))
            coordinator.onForegroundWindow("own-overlay", stableAppWindow = false)
            coordinator.onForegroundWindow("launcher", stableAppWindow = false)
            coordinator.onForegroundWindow("other-app", stableAppWindow = true)
            yield()
            assertFalse("Only the resolved launcher confirms HOME", navigation.isCompleted)
            coordinator.onForegroundWindow("launcher", stableAppWindow = true)
            assertTrue(navigation.await())
            assertEquals("launcher", coordinator.stableForegroundPackage())
            assertFalse(coordinator.awaitingStableExit("one"))
        } finally {
            homeJob.cancelAndJoin()
        }
    }

    @Test fun sessionExitAlreadyOnObservedLauncherNeedsNoDuplicateHomeCommand() = runBlocking {
        coordinator.setAccessibilityConnected(true, launcherPackage = "launcher")
        coordinator.onForegroundWindow("launcher", stableAppWindow = true)
        var commands = 0
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { commands++ }
        }
        try {
            assertTrue(coordinator.requestSessionHome("one"))
            assertEquals(0, commands)
        } finally {
            homeJob.cancelAndJoin()
        }
    }

    @Test fun rejectedSessionHomeDoesNotSynthesizeExitOrLeaveEchoSuppression() = runBlocking {
        coordinator.setAccessibilityConnected(true, launcherPackage = "launcher")
        coordinator.onForegroundWindow("one", stableAppWindow = true)
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                coordinator.markHomeRequested(command.packageName)
                command.result.complete(false)
            }
        }
        try {
            assertFalse(coordinator.requestSessionHome("one"))
            assertEquals("one", coordinator.stableForegroundPackage())
            assertFalse(coordinator.awaitingStableExit("one"))
        } finally {
            homeJob.cancelAndJoin()
        }
    }

    @Test fun disconnectCannotConfirmSessionHome() = runBlocking {
        coordinator.setAccessibilityConnected(true, launcherPackage = "launcher")
        coordinator.onForegroundWindow("one", stableAppWindow = true)
        val acknowledged = CompletableDeferred<Unit>()
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                command.result.complete(true)
                acknowledged.complete(Unit)
            }
        }
        try {
            val navigation = async(start = CoroutineStart.UNDISPATCHED) { coordinator.requestSessionHome("one") }
            acknowledged.await()
            coordinator.setAccessibilityConnected(false)
            assertFalse(navigation.await())
            assertFalse(coordinator.requestSessionHome("one"))
        } finally {
            homeJob.cancelAndJoin()
        }
    }

    @Test fun missingLauncherEventTimesOutWithoutPretendingTheTargetExited() = runBlocking {
        coordinator.setAccessibilityConnected(true, launcherPackage = "launcher")
        coordinator.onForegroundWindow("one", stableAppWindow = true)
        val homeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.accessibilityCommands.collect { command ->
                coordinator.markHomeRequested(command.packageName)
                command.result.complete(true)
            }
        }
        try {
            assertFalse(coordinator.requestSessionHome("one"))
            assertTrue(coordinator.isObservedForeground("one"))
            assertFalse(coordinator.awaitingStableExit("one"))
        } finally {
            homeJob.cancelAndJoin()
        }
    }

    @Test fun sessionReminderIsNotSilentlyDroppedWithoutAccessibility() {
        coordinator.updateManagedPackages(setOf("one"))
        friction.beginPhysicalSession("one", 600L)
        friction.onActualUsage("one", 600L)
        assertTrue(friction.isReminderDue("one"))
        assertEquals(InterventionType.SESSION_REMINDER, selectInterventionType(
            dailyLimitTriggered = false,
            sessionDue = friction.isReminderDue("one") && coordinator.interventionsAllowed("one"),
            overlayShowing = coordinator.hasOverlay(),
        ))
    }

    @Test fun disconnectedAccessibilityOnlyRemovesEnhancedActions() {
        assertEquals(SessionReminderMode.OVERLAY_ONLY, coordinator.sessionReminderMode("one"))
        assertFalse(coordinator.enhancedInterventionsAllowed("one"))
        coordinator.setAccessibilityConnected(true)
        assertEquals(SessionReminderMode.HOME, coordinator.sessionReminderMode("one"))
        assertTrue(coordinator.enhancedInterventionsAllowed("one"))
        coordinator.setAccessibilityConnected(false)
        assertEquals(SessionReminderMode.OVERLAY_ONLY, coordinator.sessionReminderMode("one"))
        assertTrue(coordinator.canTime("one"))
    }

    @Test fun disconnectedFocusCannotSilentlySuppressOrdinaryReminders() {
        coordinator.updateFocus(FocusModeState(FocusModeType.FOCUS, startTimeMillis = 500L,
            endTimeMillis = 10_000L, blockedPackages = setOf("one")))
        assertEquals(SessionReminderMode.OVERLAY_ONLY, coordinator.sessionReminderMode("one"))
        assertTrue(coordinator.claimOverlay(InterventionPriority.SESSION, "one"))
        coordinator.releaseOverlay(InterventionPriority.SESSION, "one")
        coordinator.setAccessibilityConnected(true)
        assertNull(coordinator.sessionReminderMode("one"))
        assertFalse(coordinator.claimOverlay(InterventionPriority.SESSION, "one"))
    }

    @Test fun dailyAvailableWithoutAccessibilityTakesPriorityOverDueSessionReminder() {
        coordinator.publishDaily("one", DailyLimitSnapshot("day", 600L, 600L, true))
        assertEquals(InterventionType.DAILY_LIMIT_REACHED, selectInterventionType(
            dailyLimitTriggered = coordinator.dailySnapshot("one")!!.isOverLimit &&
                coordinator.interventionsAllowed("one"),
            sessionDue = coordinator.interventionsAllowed("one"),
            overlayShowing = false,
        ))
    }

    private fun activateFriction() {
        friction.beginPhysicalSession("one", 600L)
        friction.onActualUsage("one", 600L)
        val gate = friction.beginGate("one", SessionGatePhase.REMINDER)!!
        friction.onReminderShown("one", gate.id)
        friction.endPhysicalSession("one", 600L)
        friction.closeGate("one", gate.id)
    }

    @Test fun accessibilityDisconnectPreservesEpisodeAndDoesNotRestartFirstThreshold() {
        coordinator.setAccessibilityConnected(true)
        activateFriction()
        val before = friction.snapshot("one")!!
        coordinator.setAccessibilityConnected(false)
        val after = friction.snapshot("one")
        assertNotNull("Disconnect must not erase reminder progression", after)
        assertEquals(before.episodeUsageSeconds, after!!.episodeUsageSeconds)
        assertEquals(before.reminderCount, after.reminderCount)
        assertEquals(before.nextReminderAtSeconds, after.nextReminderAtSeconds)
        assertFalse(after.reentryGateArmed)
    }
}
