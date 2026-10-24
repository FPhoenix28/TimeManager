package com.example.timemanager.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.preferences.*
import com.example.timemanager.data.repository.*
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.monitor.SessionFrictionManager
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDetailPrecisionTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private fun store(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
        folder.root.resolve("$name.preferences_pb")
    }
    @After fun close() { scope.cancel() }

    @Test fun dailySecondsRoundTripPerPackageAndLegacyKeyUntouched() = runBlocking {
        val data = store("daily")
        val prefs = AppLimitPreferences(data)
        val repo = LimitRepository(prefs, diagnostic = {})
        repo.saveLimit(AppLimit("a", 30))
        repo.saveLimit(AppLimit("b", 15))
        repo.saveLimitSeconds("a", 20L)
        assertEquals(20L, repo.observeLimit("a").first()!!.limitSeconds)
        assertEquals(900L, repo.observeLimit("b").first()!!.limitSeconds)
        assertEquals(30, data.data.first()[intPreferencesKey("daily_limit_minutes:a")])
        repo.saveLimit(repo.observeLimit("a").first()!!)
        assertEquals(20L, repo.observeLimit("a").first()!!.limitSeconds)
        repo.saveLimitSeconds("a", 60L)
        assertEquals(60L, LimitRepository(prefs, diagnostic = {}).observeLimit("a").first()!!.limitSeconds)
        assertEquals(60L, repo.observeAllLimits().first().first { it.packageName == "a" }.limitSeconds)
    }

    @Test fun sessionTwentySecondsRoundTripAndLegacyTenFifteenMinutes() = runBlocking {
        val prefs = SessionSettingsPreferences(store("session"))
        val repo = SessionSettingsRepository(prefs)
        assertEquals(600L, repo.observe("unset").first().reminderSeconds)
        repo.save("a", 10)
        repo.save("b", 15)
        assertEquals(600L, repo.observe("a").first().reminderSeconds)
        assertEquals(900L, repo.observe("b").first().reminderSeconds)
        repo.saveSeconds("a", 20L)
        assertEquals(20L, SessionSettingsRepository(prefs).observe("a").first().reminderSeconds)
        assertEquals(20L, repo.observeStoredSeconds().first()["a"])
        repo.saveSeconds("a", 90L)
        assertEquals(90L, repo.observe("a").first().reminderSeconds)
    }

    @Test fun ordinaryMinuteSaveClearsPrecisionOverrideAndRemoveClearsBoth() = runBlocking {
        val limits = LimitRepository(AppLimitPreferences(store("limits")), diagnostic = {})
        val sessions = SessionSettingsRepository(SessionSettingsPreferences(store("sessions")))
        limits.saveLimitSeconds("a", 20L)
        limits.saveLimit(AppLimit("a", 30))
        assertEquals(1800L, limits.observeLimit("a").first()!!.limitSeconds)
        sessions.saveSeconds("a", 20L)
        sessions.save("a", 15)
        assertEquals(900L, sessions.observe("a").first().reminderSeconds)
        limits.removeLimit("a")
        sessions.remove("a")
        assertNull(limits.observeLimit("a").first())
        assertEquals(600L, sessions.observe("a").first().reminderSeconds)
        assertFalse(sessions.observeStoredSeconds().first().containsKey("a"))
    }

    @Test fun precisionFeedsActualEffectiveQuotaEvaluatorAndFriction() = runBlocking {
        val limits = LimitRepository(AppLimitPreferences(store("limit")), diagnostic = {})
        val sessions = SessionSettingsRepository(SessionSettingsPreferences(store("session")))
        limits.saveLimitSeconds("a", 60L)
        sessions.saveSeconds("a", 20L)
        val manager = LimitInterventionManager(InterventionRepository(InterventionPreferences(store("runtime"))), UsageDayProvider())
        val effective = manager.getEffectiveLimitSeconds("a", limits.observeLimit("a").first()!!.limitSeconds)
        assertEquals(60L, effective.effectiveLimitSeconds)
        assertFalse(UsageLimitEvaluator().evaluateSeconds("a", 59L, effective.effectiveLimitSeconds).isLimitReached)
        assertTrue(UsageLimitEvaluator().evaluateSeconds("a", 60L, effective.effectiveLimitSeconds).isLimitReached)
        val friction = SessionFrictionManager(clock = { 0L })
        friction.beginPhysicalSession("a", sessions.observe("a").first().reminderSeconds)
        friction.onActualUsage("a", 19L)
        assertFalse(friction.isReminderDue("a"))
        friction.onActualUsage("a", 20L)
        assertTrue(friction.isReminderDue("a"))
    }

    @Test fun extensionRepositoryAddsProductionAndDebugAmountsWithoutRewritingHistory() = runBlocking {
        val repo = InterventionRepository(InterventionPreferences(store("extension")))
        val production = TimeConfig.production().extensionDurationSeconds
        assertEquals(900L, repo.grantExtension("prod", "2026-10-23", production).todayExtensionSeconds)
        assertEquals(1800L, repo.grantExtension("prod", "2026-10-23", production).todayExtensionSeconds)
        assertEquals(30L, repo.grantExtension("dev", "2026-10-23", 30L).todayExtensionSeconds)
        assertEquals(60L, repo.grantExtension("dev", "2026-10-23", 30L).todayExtensionSeconds)
        assertEquals(1830L, repo.grantExtension("prod", "2026-10-23", 30L).todayExtensionSeconds)
    }

    @Test fun zeroOrNegativePreciseSettingsAreNotPersisted() = runBlocking {
        val prefs = AppLimitPreferences(store("invalid"))
        val sessions = SessionSettingsPreferences(store("invalid_session"))
        for (invalid in listOf(0L, -1L, 360_000L)) {
            assertTrue(runCatching { prefs.setLimitSeconds("a", invalid) }.isFailure)
            assertTrue(runCatching { sessions.setReminderSeconds("a", invalid) }.isFailure)
        }
        assertNull(prefs.observeLimitSeconds("a").first())
        assertNull(sessions.observeReminderSeconds("a").first())
    }

    @Test fun globalResetPreservesActualPerAppDataAndCurrentEpisode() = runBlocking {
        val limits = LimitRepository(AppLimitPreferences(store("reset_daily")), diagnostic = {})
        val sessions = SessionSettingsRepository(SessionSettingsPreferences(store("reset_session")))
        limits.saveLimitSeconds("a", 60L)
        sessions.saveSeconds("a", 20L)
        val configStore = object : DebugTimingStorage {
            var config = TimeConfig.production()
            override fun read() = config
            override fun write(config: TimeConfig) { this.config = config }
            override fun clear() { config = TimeConfig.production() }
        }
        val controller = DebugTimingController(configStore)
        controller.save(DebugTimingParameter.INCREMENT_FIRST, 15L)
        val friction = SessionFrictionManager(clock = { 0L }, timeConfigProvider = controller)
        friction.beginPhysicalSession("a", sessions.observe("a").first().reminderSeconds)
        friction.onActualUsage("a", 15L)
        val before = friction.snapshot("a")
        controller.reset()
        assertEquals(before, friction.snapshot("a"))
        assertEquals(60L, limits.observeLimit("a").first()!!.limitSeconds)
        assertEquals(20L, sessions.observe("a").first().reminderSeconds)
    }

    @Test fun secondsSurviveColdDataStoreRecreation() = runBlocking {
        val file = folder.root.resolve("cold.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file }
        LimitRepository(AppLimitPreferences(firstStore), diagnostic = {}).saveLimitSeconds("a", 20L)
        firstJob.cancelAndJoin()
        val reloaded = PreferenceDataStoreFactory.create(scope = scope) { file }
        assertEquals(20L, LimitRepository(AppLimitPreferences(reloaded), diagnostic = {}).observeLimit("a").first()!!.limitSeconds)
    }

    @Test fun detailUiStateKeepsPreciseDurationsAndEffectiveAllowance() = runBlocking {
        val limits = LimitRepository(AppLimitPreferences(store("ui_daily")), diagnostic = {})
        val sessions = SessionSettingsRepository(SessionSettingsPreferences(store("ui_session")))
        limits.saveLimitSeconds("a", 90L)
        sessions.saveSeconds("a", 20L)
        val state = com.example.timemanager.ui.detail.ManagedAppDetailUiState.Ready(
            packageName = "a", appName = "App", appIcon = null, isInstalled = true,
            todayUsageSeconds = 10L, baseLimitSeconds = limits.observeLimit("a").first()!!.limitSeconds,
            extensionSeconds = 30L, effectiveLimitSeconds = 120L, remainingSeconds = 110L,
            exceededSeconds = 0L, sessionReminderMinutes = 0, isOverLimit = false,
            sessionReminderSeconds = sessions.observe("a").first().reminderSeconds,
        )
        assertEquals("1分30秒", com.example.timemanager.ui.detail.formatAppDetailDuration(state.baseLimitSeconds!!))
        assertEquals("20秒", com.example.timemanager.ui.detail.formatAppDetailDuration(state.sessionReminderSeconds))
        assertEquals(state.baseLimitSeconds!! + state.extensionSeconds, state.effectiveLimitSeconds)
    }
}
