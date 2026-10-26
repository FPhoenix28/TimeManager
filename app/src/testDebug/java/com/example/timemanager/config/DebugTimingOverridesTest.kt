package com.example.timemanager.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DebugTimingOverridesTest {
    private class MemoryStorage : DebugTimingStorage {
        var saved = TimeConfig.production()
        override fun read() = saved
        override fun write(config: TimeConfig) { saved = config }
        override fun clear() { saved = TimeConfig.production() }
    }

    @Test fun defaultsAreProductionAndNoGlobalFirstReminderOverride() {
        val c = DebugTimingController(MemoryStorage())
        assertEquals(TimeConfig.production(), c.current())
        assertNull(c.current().sessionInterventionOverrideSeconds)
    }
    @Test fun everyAdvancedParameterCanBeSavedAndRestored() {
        val store = MemoryStorage()
        val c = DebugTimingController(store)
        for ((parameter, seconds) in listOf(DebugTimingParameter.INCREMENT_FIRST to 15L,
            DebugTimingParameter.INCREMENT_SECOND to 15L, DebugTimingParameter.INCREMENT_REPEAT to 10L,
            DebugTimingParameter.IDLE_RESET to 30L, DebugTimingParameter.GATE to 2L,
            DebugTimingParameter.EXTENSION to 30L, DebugTimingParameter.NOTIFICATION_FIRST to 15L,
            DebugTimingParameter.NOTIFICATION_SECOND to 30L)) {
            c.save(parameter, seconds)
            assertEquals(seconds, parameter.seconds(DebugTimingController(store).current()))
        }
        assertNull(c.current().sessionInterventionOverrideSeconds)
        c.reset()
        assertEquals(TimeConfig.production(), DebugTimingController(store).current())
    }
    @Test fun invalidSaveDoesNotChangeCurrentConfig() {
        val c = DebugTimingController(MemoryStorage())
        for (value in listOf(0L, -1L, 360_000L)) {
            assertThrows(IllegalArgumentException::class.java) { c.save(DebugTimingParameter.GATE, value) }
        }
        assertEquals(TimeConfig.production(), c.current())
    }
    @Test fun invalidNotificationOrderingIsRejected() {
        val c = DebugTimingController(MemoryStorage())
        assertThrows(IllegalArgumentException::class.java) { c.save(DebugTimingParameter.NOTIFICATION_SECOND, 900L) }
        assertEquals(TimeConfig.production(), c.current())
    }
    @Test fun actualControllerFeedsProgressionGateAndNotificationHelpers() {
        val c = DebugTimingController(MemoryStorage())
        c.save(DebugTimingParameter.INCREMENT_FIRST, 15L)
        c.save(DebugTimingParameter.INCREMENT_SECOND, 15L)
        c.save(DebugTimingParameter.INCREMENT_REPEAT, 10L)
        c.save(DebugTimingParameter.GATE, 2L)
        c.save(DebugTimingParameter.NOTIFICATION_FIRST, 15L)
        c.save(DebugTimingParameter.NOTIFICATION_SECOND, 30L)
        val friction = com.example.timemanager.monitor.SessionFrictionManager(clock = { 0L }, timeConfigProvider = c)
        friction.beginPhysicalSession("a", 20L)
        for (at in listOf(20L, 35L, 50L, 60L, 70L, 80L)) {
            friction.onActualUsage("a", at)
            assertTrue(friction.isReminderDue("a"))
            val gate = friction.beginGate("a", com.example.timemanager.data.model.SessionGatePhase.REMINDER)!!
            assertEquals(2_000L, gate.endTimeMillis)
            friction.onReminderShown("a", gate.id)
            friction.closeGate("a", gate.id)
        }
        assertEquals(90L, friction.snapshot("a")!!.nextReminderAtSeconds)
        val tracker = com.example.timemanager.monitor.SessionTracker(timeConfigProvider = c)
        assertEquals(com.example.timemanager.data.model.SessionReminder.FIFTEEN_MINUTES,
            tracker.update(com.example.timemanager.data.model.TimerState("a", "App", 15L)))
        assertEquals(com.example.timemanager.data.model.SessionReminder.THIRTY_MINUTES,
            tracker.update(com.example.timemanager.data.model.TimerState("a", "App", 30L)))
    }
    @Test fun providerPublishesLatestDisplayedExtensionAndResetsLive() = runBlocking {
        val c = DebugTimingController(MemoryStorage())
        c.save(DebugTimingParameter.EXTENSION, 30L)
        assertEquals(30L, c.observe().first().extensionDurationSeconds)
        c.save(DebugTimingParameter.EXTENSION, 150L)
        assertEquals(150L, c.observe().first().extensionDurationSeconds)
        assertEquals("2分30秒", formatPreciseDuration(c.current().extensionDurationSeconds))
        c.reset()
        assertEquals(900L, c.observe().first().extensionDurationSeconds)
    }
}
