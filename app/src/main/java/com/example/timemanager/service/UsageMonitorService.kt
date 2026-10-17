package com.example.timemanager.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.example.timemanager.data.model.ForegroundApp
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.config.BuildTimeConfig
import com.example.timemanager.config.TimeConfig
import com.example.timemanager.config.InterventionRuntime
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.intervention.EntryCheckResult
import com.example.timemanager.domain.intervention.InterventionPriority
import com.example.timemanager.domain.intervention.MonitorInterventionCommand
import com.example.timemanager.domain.intervention.SessionReminderMode
import com.example.timemanager.domain.intervention.InterventionPresentation
import com.example.timemanager.domain.intervention.InterventionPresentationPolicy
import com.example.timemanager.domain.intervention.SessionCompletionAction
import com.example.timemanager.permissions.PermissionHealthManager
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.data.repository.InstalledAppInfoProvider
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.preferences.InterventionPreferences
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.preferences.TrackedAppPreferences
import com.example.timemanager.data.preferences.SessionSettingsPreferences
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.data.repository.SessionSettingsRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.data.model.LimitInterventionDecision
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.monitor.ForegroundAppDetector
import com.example.timemanager.monitor.SessionTracker
import com.example.timemanager.notifications.MonitorNotificationHelper
import com.example.timemanager.notifications.NotificationHelper
import com.example.timemanager.notifications.NotificationIds
import com.example.timemanager.notifications.UsageLimitNotifier
import com.example.timemanager.timer.AppTimer
import com.example.timemanager.ui.intervention.InterventionOverlayController
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/** User-visible foreground service that owns the foreground-app monitoring loop. */
class UsageMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coordinator = InterventionRuntime.coordinator
    private val friction get() = coordinator.friction
    private val permissionHealthManager by lazy { PermissionHealthManager.get(applicationContext) }
    private lateinit var foregroundAppDetector: ForegroundAppDetector
    private lateinit var appTimer: AppTimer
    private lateinit var notificationHelper: MonitorNotificationHelper
    private lateinit var usageTimeRepository: UsageTimeRepository
    private lateinit var trackedAppRepository: TrackedAppRepository
    private lateinit var limitRepository: LimitRepository
    private lateinit var limitEvaluator: UsageLimitEvaluator
    private lateinit var usageLimitNotifier: UsageLimitNotifier
    private lateinit var interventionManager: LimitInterventionManager
    private lateinit var interventionOverlayController: InterventionOverlayController
    private lateinit var usageDayProvider: UsageDayProvider
    private lateinit var sessionTracker: SessionTracker
    private lateinit var alertNotificationHelper: NotificationHelper
    private lateinit var sessionSettingsRepository: SessionSettingsRepository
    private lateinit var powerManager: PowerManager
    private lateinit var timeConfig: TimeConfig
    private var detectionJob: Job? = null
    private var trackedAppsJob: Job? = null
    private var limitsJob: Job? = null
    private var sessionSettingsJob: Job? = null
    private var interventionCommandsJob: Job? = null
    private var permissionHealthJob: Job? = null
    private var trackedAppsLoaded = false
    private var limitsLoaded = false
    private var currentForegroundApp: ForegroundApp? = null
    private var timedForegroundApp: ForegroundApp? = null
    private var trackedPackageNames: Set<String> = emptySet()
    private var limitMinutesByPackage: Map<String, Int> = emptyMap()
    private var sessionReminderMinutesByPackage: Map<String, Int> = emptyMap()
    private var activeInterventionType: InterventionType? = null
    private var activeInterventionPackageName: String? = null
    private var activeInterventionRequest: LimitInterventionRequest? = null
    private var lastIdleUsageDayKey: String? = null
    private var lastIdleTrackedPackages: Set<String> = emptySet()
    private val timingMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        permissionHealthManager.refresh()
        notificationHelper = MonitorNotificationHelper(applicationContext)
        startAsForegroundService()
        foregroundAppDetector = ForegroundAppDetector(applicationContext)
        powerManager = getSystemService(PowerManager::class.java)
        timeConfig = BuildTimeConfig.value
        appTimer = AppTimer()
        sessionTracker = SessionTracker(
            firstReminderSeconds = timeConfig.firstSessionReminderSeconds,
            secondReminderSeconds = timeConfig.secondSessionReminderSeconds,
        )
        usageDayProvider = UsageDayProvider()
        usageTimeRepository = UsageTimeRepository(
            usageRepository = UsageRepository(UsageStatsReader(applicationContext)),
            preferences = RealtimeUsagePreferences(applicationContext),
            historyRepository = UsageHistoryRepository(
                AppDatabase.getInstance(applicationContext).usageHistoryDao(),
            ),
            usageDayProvider = usageDayProvider,
        )
        trackedAppRepository = TrackedAppRepository(
            TrackedAppPreferences(applicationContext),
        )
        limitRepository = LimitRepository(AppLimitPreferences(applicationContext))
        sessionSettingsRepository = SessionSettingsRepository(
            SessionSettingsPreferences(applicationContext),
        )
        val notificationStateRepository = NotificationStateRepository(
            NotificationStatePreferences(applicationContext),
        )
        limitEvaluator = UsageLimitEvaluator()
        alertNotificationHelper = NotificationHelper(applicationContext).also {
            it.createNotificationChannel()
        }
        usageLimitNotifier = UsageLimitNotifier(
            stateRepository = notificationStateRepository,
            notificationHelper = alertNotificationHelper,
            usageDayProvider = usageDayProvider,
        )
        interventionManager = LimitInterventionManager(
            repository = InterventionRepository(
                InterventionPreferences(applicationContext),
            ),
            usageDayProvider = usageDayProvider,
            timeConfig = timeConfig,
        )
        interventionOverlayController = InterventionOverlayController(applicationContext)
        Log.d(TAG, "timeConfig=$timeConfig")
        observeTrackedApps()
        observeLimits()
        observeSessionSettings()
        observeInterventionCommands()
        observePermissionHealth()
        startDetection()
        Log.d(TAG, "Usage monitor service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        permissionHealthManager.refresh()
        startDetection()
        return START_STICKY
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        trackedAppsJob?.cancel()
        limitsJob?.cancel()
        sessionSettingsJob?.cancel()
        interventionCommandsJob?.cancel()
        permissionHealthJob?.cancel()
        val finalTimerState = appTimer.finishCurrentSession()
        sessionTracker.endSession()
        runBlocking(Dispatchers.IO) {
            finalTimerState?.let { usageTimeRepository.recordTimerState(it) }
            usageTimeRepository.flush()
        }
        runBlocking {
            interventionOverlayController.dismiss()
        }
        coordinator.monitorStopped()
        serviceScope.cancel()
        Log.d(TAG, "Usage monitor service stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDetection() {
        if (detectionJob?.isActive == true) return

        detectionJob = serviceScope.launch {
            var queryStartTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS
            var lastHealthCheckMillis = 0L

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val elapsedNow = android.os.SystemClock.elapsedRealtime()
                    if (elapsedNow - lastHealthCheckMillis >= PERMISSION_REFRESH_INTERVAL_MILLIS) {
                        permissionHealthManager.refresh()
                        lastHealthCheckMillis = elapsedNow
                    }
                    foregroundAppDetector.detect(queryStartTime, now)?.let(::handleDetectedApp)
                    if (!powerManager.isInteractive) {
                        currentForegroundApp = null
                        timingMutex.withLock {
                            if (timedForegroundApp != null) {
                                finishTrackedSession()
                            } else {
                                updateIdleNotificationIfNeeded()
                            }
                        }
                    } else {
                        timingMutex.withLock {
                            friction.expireIdleEpisodes()
                            currentForegroundApp?.let { foregroundApp ->
                                if (timedForegroundApp != null &&
                                    !coordinator.canTime(checkNotNull(timedForegroundApp).packageName)
                                ) finishTrackedSession()
                                processForegroundApp(foregroundApp)
                            }
                        }
                    }
                    queryStartTime = now - EVENT_OVERLAP_MILLIS
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val pkg = timedForegroundApp?.packageName ?: activeInterventionPackageName
                    pkg?.let { friction.failOpen(it); coordinator.suppressCurrentVisit(it) }
                    timingMutex.withLock { dismissActiveIntervention() }
                    Log.e(TAG, "intervention failed but monitoring continues", error)
                }
                delay(DETECTION_INTERVAL_MILLIS)
            }
        }
    }

    private fun observeTrackedApps() {
        if (trackedAppsJob?.isActive == true) return
        trackedAppsJob = serviceScope.launch {
            trackedAppRepository.observeTrackedPackageNames().collect { packageNames ->
                timingMutex.withLock {
                    trackedPackageNames = packageNames
                    trackedAppsLoaded = true
                    val timedApp = timedForegroundApp
                    if (timedApp != null && timedApp.packageName !in packageNames) {
                        finishTrackedSession()
                    }
                    if (activeInterventionPackageName !in packageNames) dismissActiveIntervention()
                    coordinator.updateManagedPackages(packageNames)
                }
                Log.d(TAG, "Tracked apps updated: ${packageNames.size}")
            }
        }
    }

    private suspend fun processForegroundApp(foregroundApp: ForegroundApp) {
        if (!permissionHealthManager.state.value.usageAccessGranted) {
            if (timedForegroundApp != null) finishTrackedSession()
            return
        }
        val pkg = foregroundApp.packageName
        if (coordinator.isTimingPausedForOverlay()) return
        if (pkg !in trackedPackageNames) {
            if (timedForegroundApp != null) finishTrackedSession()
            else updateIdleNotificationIfNeeded()
            return
        }
        if (!coordinator.canTime(pkg)) return
        val isNewSession = timedForegroundApp?.packageName != pkg
        if (isNewSession && timedForegroundApp != null) finishTrackedSession()
        val configuredSeconds = configuredReminderSeconds(pkg)
        if (isNewSession) {
            interventionManager.onManagedAppEntered(pkg)
        }
        // Capability loss removes admission, not the episode or the AppTimer clock.
        if (isNewSession || friction.snapshot(pkg) == null) {
            friction.beginPhysicalSession(pkg, configuredSeconds)
        } else {
            friction.updateFirstReminder(pkg, configuredSeconds)
        }
        val timerState = appTimer.update(foregroundApp)
        friction.onActualUsage(pkg, timerState.currentSessionSeconds)
        val episode = friction.snapshot(pkg)
        val sessionDue = friction.isReminderDue(pkg)
        sessionTracker.update(timerState)?.let { reminder ->
            alertNotificationHelper.sendSessionReminder(pkg, foregroundApp.appName, reminder)
        }
        timedForegroundApp = foregroundApp
        lastIdleUsageDayKey = null
        lastIdleTrackedPackages = emptySet()
        val todayUsageSeconds = usageTimeRepository.recordTimerState(timerState)
        val effectiveLimit = limitMinutesByPackage[pkg]?.let {
            interventionManager.getEffectiveLimit(pkg, it)
        }
        val evaluation = effectiveLimit?.let {
            limitEvaluator.evaluateSeconds(pkg, todayUsageSeconds, it.effectiveLimitSeconds)
        }
        coordinator.publishDaily(pkg, DailyLimitSnapshot(
            usageDayProvider.currentUsageDay().key, todayUsageSeconds, effectiveLimit?.effectiveLimitSeconds,
            evaluation?.isLimitReached == true,
        ))
        evaluation?.let { usageLimitNotifier.notifyIfNeeded(foregroundApp.appName, it) }
        val notificationUpdated = notificationHelper.updateRunningNotification(timerState, todayUsageSeconds, evaluation)
        var dailyTriggered = false
        val dailyAvailable = InterventionPresentationPolicy.canShow(
            permissionHealthManager.state.value, coordinator.interventionsAllowed(pkg),
        )
        if (evaluation != null && dailyAvailable) {
            val decision = interventionManager.evaluate(
                foregroundApp.appName, evaluation, checkNotNull(effectiveLimit).baseLimitSeconds,
                timerState.currentSessionSeconds, isNewSession,
            )
            dailyTriggered = decision is LimitInterventionDecision.Show
            handleInterventionDecision(decision)
        } else if (activeInterventionType == InterventionType.DAILY_LIMIT_REACHED &&
            activeInterventionPackageName == pkg && evaluation?.isLimitReached != true
        ) {
            dismissActiveIntervention()
        }
        val selected = selectInterventionType(
            dailyLimitTriggered = dailyTriggered || (evaluation?.isLimitReached == true &&
                dailyAvailable),
            sessionDue = sessionDue && permissionHealthManager.state.value.canShowSessionReminder &&
                coordinator.interventionsAllowed(pkg),
            overlayShowing = coordinator.hasOverlay(),
        )
        val didTrigger = if (selected == InterventionType.SESSION_REMINDER) {
            showSessionGate(foregroundApp, SessionGatePhase.REMINDER, todayUsageSeconds)
        } else false
        if (isNewSession || sessionDue || timerState.currentSessionSeconds % DIAGNOSTIC_LOG_INTERVAL_SECONDS == 0L) {
            Log.d(SESSION_TRACKER_TAG,
                "SessionReminder: pkg=$pkg physicalSeconds=${timerState.currentSessionSeconds} " +
                    "episodeSeconds=${episode?.episodeUsageSeconds} first=${episode?.firstReminderSeconds} " +
                    "count=${episode?.reminderCount} next=${episode?.nextReminderAtSeconds} " +
                    "type=$selected overlayVisible=${coordinator.hasOverlay()} didTrigger=$didTrigger",
            )
            Log.d(TAG, "foregroundServiceRunning=true foregroundNotificationUpdated=$notificationUpdated " +
                "pkg=$pkg todayUsageSeconds=$todayUsageSeconds effectiveLimitSeconds=${evaluation?.limitSeconds}")
        }
    }

    private fun configuredReminderSeconds(packageName: String): Long =
        timeConfig.sessionInterventionOverrideSeconds ?: (
            (sessionReminderMinutesByPackage[packageName] ?: SessionSettingsRepository.DEFAULT_REMINDER_MINUTES) *
                SECONDS_PER_MINUTE
            )

    private fun observePermissionHealth() {
        permissionHealthJob = serviceScope.launch {
            permissionHealthManager.state.collect { health ->
                Log.d("AccessibilityRuntime", "enabled=${health.accessibilityGranted} " +
                    "connected=${health.accessibilityConnected} state=${health.accessibilityRuntimeState}")
                timingMutex.withLock {
                    if (!health.usageAccessGranted && timedForegroundApp != null) finishTrackedSession()
                    activeInterventionRequest?.let { request ->
                        if (InterventionPresentationPolicy.shouldDismiss(request, health)) dismissActiveIntervention()
                    }
                }
            }
        }
    }

    private fun observeLimits() {
        if (limitsJob?.isActive == true) return
        limitsJob = serviceScope.launch {
            limitRepository.observeAllLimits().collect { limits ->
                timingMutex.withLock {
                    limitMinutesByPackage = limits.associate { limit ->
                        limit.packageName to limit.limitMinutes
                    }
                    limitsLoaded = true
                }
                Log.d(TAG, "App limits updated: ${limits.size}")
                limits.forEach { limit ->
                    Log.d(
                        TAG,
                        "observedLimit packageName=${limit.packageName} " +
                            "limitMinutes=${limit.limitMinutes}",
                    )
                }
            }
        }
    }

    private fun observeSessionSettings() {
        if (sessionSettingsJob?.isActive == true) return
        sessionSettingsJob = serviceScope.launch {
            sessionSettingsRepository.observeStoredSettings().collect { settings ->
                timingMutex.withLock { sessionReminderMinutesByPackage = settings }
                Log.d(SESSION_TRACKER_TAG, "session settings updated count=${settings.size}")
            }
        }
    }

    private suspend fun finishTrackedSession(
        keepIntervention: Boolean = activeInterventionType != null,
        overlayPause: Boolean = false,
    ) {
        val finalState = appTimer.finishCurrentSession()
        finalState?.let {
            friction.endPhysicalSession(it.packageName, it.currentSessionSeconds)
            interventionManager.onAppLeft(it.packageName, keepVisibleIntervention = keepIntervention)
            val usage = usageTimeRepository.recordTimerState(it)
            coordinator.dailySnapshot(it.packageName)?.let { snapshot ->
                if (snapshot.usageDayKey == usageDayProvider.currentUsageDay().key) {
                    val over = snapshot.effectiveLimitSeconds?.let { limit ->
                        limitEvaluator.evaluateSeconds(it.packageName, usage, limit).isLimitReached
                    } == true
                    coordinator.publishDaily(it.packageName, snapshot.copy(usedSeconds = usage, isOverLimit = over))
                }
            }
            usageTimeRepository.finishPhysicalSession(overlayPause)
            Log.d(TAG, "physical session ended package=${it.packageName}; episode retained")
        }
        timedForegroundApp = null
        sessionTracker.endSession()
        updateIdleNotificationIfNeeded(force = true)
    }

    private suspend fun goHomeAndFinish(packageName: String): Boolean {
        if (!coordinator.enhancedInterventionsAllowed(packageName) || !coordinator.requestHome(packageName)) {
            coordinator.suppressCurrentVisit(packageName)
            Log.w(TAG, "HOME unavailable; intervention skipped, monitoring continues package=$packageName")
            return false
        }
        currentForegroundApp = null
        finishTrackedSession(keepIntervention = true)
        return true
    }

    private suspend fun dismissActiveIntervention() {
        val request = activeInterventionRequest
        interventionOverlayController.dismiss()
        if (request != null) {
            if (request.type == InterventionType.DAILY_LIMIT_REACHED) {
                interventionManager.closeVisibleIntervention(request.packageName)
            } else {
                request.sessionGate?.let { friction.closeGate(request.packageName, it.id) }
            }
        }
        clearActiveIntervention()
    }

    private fun clearActiveIntervention() {
        val pkg = activeInterventionPackageName
        val type = activeInterventionType
        if (pkg != null && type != null) {
            coordinator.releaseOverlay(
                if (type == InterventionType.DAILY_LIMIT_REACHED) InterventionPriority.DAILY else InterventionPriority.SESSION,
                pkg,
            )
        }
        activeInterventionType = null
        activeInterventionPackageName = null
        activeInterventionRequest = null
    }

    private suspend fun handleInterventionDecision(
        decision: LimitInterventionDecision,
        alreadyHome: Boolean = false,
    ) {
        when (decision) {
            LimitInterventionDecision.None -> Unit
            LimitInterventionDecision.Hide -> {
                if (activeInterventionType == InterventionType.DAILY_LIMIT_REACHED) dismissActiveIntervention()
            }
            is LimitInterventionDecision.Show -> {
                val presentation = if (alreadyHome) InterventionPresentation.HOME_FIRST else
                    InterventionPresentationPolicy.mode(
                        permissionHealthManager.state.value.canForceHome && coordinator.accessibilityConnected,
                    )
                val request = decision.request.copy(
                    presentation = presentation,
                    actionDeadlineMillis = android.os.SystemClock.elapsedRealtime() + 5_000L,
                )
                if (activeInterventionType == InterventionType.SESSION_REMINDER) dismissActiveIntervention()
                if (!coordinator.claimOverlay(InterventionPriority.DAILY, request.packageName)) {
                    interventionManager.closeVisibleIntervention(request.packageName)
                    return
                }
                if (presentation == InterventionPresentation.HOME_FIRST && !alreadyHome &&
                    !goHomeAndFinish(request.packageName)) {
                    coordinator.releaseOverlay(InterventionPriority.DAILY, request.packageName)
                    interventionManager.closeVisibleIntervention(request.packageName)
                    return
                }
                if (!InterventionPresentationPolicy.canShow(
                        permissionHealthManager.state.value, coordinator.interventionsAllowed(request.packageName),
                    )) {
                    coordinator.releaseOverlay(InterventionPriority.DAILY, request.packageName)
                    interventionManager.closeVisibleIntervention(request.packageName)
                    return
                }
                val shown = interventionOverlayController.show(
                    request,
                    onExit = { serviceScope.launch { timingMutex.withLock { exitActiveIntervention(request) } } },
                    onContinue = {
                        serviceScope.launch {
                            timingMutex.withLock {
                                if (activeInterventionRequest != request) return@withLock
                                if (android.os.SystemClock.elapsedRealtime() < checkNotNull(request.actionDeadlineMillis)) return@withLock
                                val base = limitMinutesByPackage[request.packageName] ?: return@withLock
                                val before = friction.snapshot(request.packageName)
                                if (interventionManager.grantConfiguredExtension(request.packageName, base * SECONDS_PER_MINUTE)) {
                                    // No relaunch, physical-session restart or episode reset.
                                    refreshDailySnapshot(request.packageName)
                                    dismissActiveIntervention()
                                    updateIdleNotificationIfNeeded(force = true)
                                    Log.d(TAG, "daily extension granted; presentation=${request.presentation} " +
                                        "package=${request.packageName} episodeBefore=$before " +
                                        "episodeAfter=${friction.snapshot(request.packageName)}")
                                }
                            }
                        }
                    },
                )
                if (shown) {
                    activeInterventionType = InterventionType.DAILY_LIMIT_REACHED
                    activeInterventionPackageName = request.packageName
                    activeInterventionRequest = request
                    if (presentation == InterventionPresentation.OVERLAY_IN_PLACE) pauseForOverlay(request)
                } else {
                    interventionManager.closeVisibleIntervention(request.packageName)
                    coordinator.releaseOverlay(InterventionPriority.DAILY, request.packageName)
                    coordinator.suppressCurrentVisit(request.packageName)
                    Log.w(TAG, "daily overlay failed but monitoring continues")
                }
            }
        }
    }

    private suspend fun showSessionGate(
        app: ForegroundApp,
        phase: SessionGatePhase,
        todayUsageSeconds: Long,
    ): Boolean {
        val pkg = app.packageName
        val health = permissionHealthManager.refresh()
        if (!health.canShowSessionReminder) return false
        val availableMode = coordinator.sessionReminderMode(pkg) ?: return false
        var mode = if (health.canForceHome) availableMode
            else SessionReminderMode.OVERLAY_ONLY
        if (phase == SessionGatePhase.REENTRY && !friction.isReentryGateArmed(pkg)) return false
        if (phase == SessionGatePhase.REENTRY && mode != SessionReminderMode.HOME) return false
        if (!coordinator.claimOverlay(InterventionPriority.SESSION, pkg)) return false
        if (mode == SessionReminderMode.HOME) {
            val confirmed = coordinator.requestSessionHome(pkg)
            Log.d(INTERVENTION_TAG, "session HOME package=$pkg confirmed=$confirmed phase=$phase")
            if (confirmed) {
                currentForegroundApp = null
                finishTrackedSession(keepIntervention = true)
            } else if (phase == SessionGatePhase.REENTRY) {
                coordinator.releaseOverlay(InterventionPriority.SESSION, pkg)
                friction.failOpen(pkg)
                return false
            } else {
                // Never describe an unconfirmed HOME request as a completed exit.
                mode = SessionReminderMode.OVERLAY_ONLY
            }
        }
        Log.d(INTERVENTION_TAG, "session presentation package=$pkg mode=$mode " +
            "accessibility=${health.accessibilityRuntimeState}")
        if (!coordinator.interventionsAllowed(pkg)) {
            coordinator.releaseOverlay(InterventionPriority.SESSION, pkg)
            return false
        }
        // Repeated requests reuse the same deadline and never add a second view.
        val gate = friction.beginGate(pkg, phase)
        if (gate == null) {
            coordinator.releaseOverlay(InterventionPriority.SESSION, pkg)
            return false
        }
        if (activeInterventionRequest?.sessionGate?.id == gate.id && interventionOverlayController.isShowing()) return true
        val episode = friction.snapshot(pkg) ?: return false
        val request = LimitInterventionRequest.sessionReminder(
            pkg, app.appName, todayUsageSeconds, episode.episodeUsageSeconds,
            episode.firstReminderSeconds, gate,
            presentation = InterventionPresentationPolicy.mode(mode == SessionReminderMode.HOME),
        )
        val shown = interventionOverlayController.show(
            request,
            onExit = { serviceScope.launch { timingMutex.withLock { finishGate(request, continueApp = false) } } },
            onContinue = { serviceScope.launch { timingMutex.withLock { finishGate(request, continueApp = true) } } },
        )
        if (shown) {
            activeInterventionType = InterventionType.SESSION_REMINDER
            activeInterventionPackageName = pkg
            activeInterventionRequest = request
            if (gate.phase == SessionGatePhase.REMINDER) {
                friction.onReminderShown(pkg, gate.id, armReentry = mode == SessionReminderMode.HOME &&
                    coordinator.accessibilityConnected && permissionHealthManager.state.value.canForceHome)
            }
            if (request.presentation == InterventionPresentation.OVERLAY_IN_PLACE) pauseForOverlay(request)
            if (com.example.timemanager.BuildConfig.DEBUG) {
                val stage = friction.snapshot(pkg)
                Log.d("SessionFrictionTransition", "package=$pkg reminderStage=${stage?.reminderCount} " +
                    "gateArmed=${stage?.reentryGateArmed} gateConsumed=${stage?.reentryGateConsumed} " +
                    "action=${if (phase == SessionGatePhase.REENTRY) "SHOW_REENTRY_GATE" else "STAGE_COOLDOWN"}")
            }
            Log.d(INTERVENTION_TAG, "gate shown package=$pkg phase=${gate.phase} mode=$mode end=${gate.endTimeMillis}")
        } else {
            friction.failOpen(pkg)
            coordinator.releaseOverlay(InterventionPriority.SESSION, pkg)
            Log.w(TAG, "session overlay failed but monitoring continues")
        }
        return shown
    }

    private suspend fun finishGate(request: LimitInterventionRequest, continueApp: Boolean) {
        val gate = request.sessionGate ?: return
        if (activeInterventionRequest?.sessionGate?.id != gate.id) return
        val action = InterventionPresentationPolicy.sessionCompletion(
            request, continueApp, android.os.SystemClock.elapsedRealtime(),
        )
        if (action == SessionCompletionAction.WAIT) return
        if (action == SessionCompletionAction.EXIT_TO_HOME) {
            exitSessionIntervention(request)
            return
        }
        if (coordinator.accessibilityConnected && coordinator.isFocusBlocking(request.packageName)) {
            dismissActiveIntervention()
            return
        }
        // Revalidate effective daily allowance before granting any session entry token.
        val (limit, evaluation) = refreshDailySnapshot(request.packageName)
        if (evaluation?.isLimitReached == true && limit != null) {
            dismissActiveIntervention()
            handleInterventionDecision(
                interventionManager.evaluate(
                    request.appName, evaluation, limit.baseLimitSeconds,
                    request.currentSessionSeconds, isNewSession = true,
                ),
                alreadyHome = request.presentation == InterventionPresentation.HOME_FIRST,
            )
            return
        }
        if (action == SessionCompletionAction.CONTINUE_IN_PLACE) {
            dismissActiveIntervention()
            Log.d(INTERVENTION_TAG, "overlay choice continue package=${request.packageName}; no launch or admission")
            return
        }
        if (!friction.allowNextEntry(request.packageName, gate.id)) return
        // Request the launch while our user-clicked overlay is still visible.
        val launched = interventionOverlayController.launchApplication(request.packageName)
        if (!launched) friction.failOpen(request.packageName)
        dismissActiveIntervention()
        Log.d(INTERVENTION_TAG, "gate continue package=${request.packageName} launchRequested=$launched")
    }

    private suspend fun refreshDailySnapshot(packageName: String): Pair<
        com.example.timemanager.data.model.EffectiveAppLimit?,
        com.example.timemanager.data.model.UsageLimitEvaluation?,
    > {
        val day = usageDayProvider.currentUsageDay().key
        val observed = coordinator.dailySnapshot(packageName)?.takeIf { it.usageDayKey == day }?.usedSeconds ?: 0L
        val used = maxOf(observed, usageTimeRepository.currentRuntimeUsage(packageName)
            ?: usageTimeRepository.getTodayUsage(packageName))
        val storedLimit = limitRepository.observeLimit(packageName).first()
        val limit = storedLimit?.let { interventionManager.getEffectiveLimit(packageName, it.limitMinutes) }
        val evaluation = limit?.let { limitEvaluator.evaluateSeconds(packageName, used, it.effectiveLimitSeconds) }
        coordinator.publishDaily(packageName, DailyLimitSnapshot(day, used, limit?.effectiveLimitSeconds,
            evaluation?.isLimitReached == true))
        Log.d(USAGE_LIMIT_EVALUATOR_TAG, "entry package=$packageName today=$used " +
            "base=${limit?.baseLimitSeconds} extension=${limit?.todayExtensionSeconds} effective=${limit?.effectiveLimitSeconds}")
        return limit to evaluation
    }

    private suspend fun pauseForOverlay(request: LimitInterventionRequest) {
        val priority = if (request.type == InterventionType.DAILY_LIMIT_REACHED)
            InterventionPriority.DAILY else InterventionPriority.SESSION
        coordinator.pauseTimingForOverlay(priority, request.packageName)
        finishTrackedSession(keepIntervention = true, overlayPause = true)
        Log.d(TAG, "overlay timing paused package=${request.packageName}; episode retained")
    }

    private suspend fun exitActiveIntervention(request: LimitInterventionRequest) {
        if (activeInterventionRequest != request) return
        try {
            if (request.presentation == InterventionPresentation.OVERLAY_IN_PLACE) {
                withContext(Dispatchers.Main.immediate) { interventionOverlayController.openHomeScreen() }
                currentForegroundApp = null
            }
        } catch (error: Exception) {
            Log.w(TAG, "user-requested HOME failed; dismissing overlay", error)
        } finally {
            dismissActiveIntervention()
        }
    }

    /** User explicitly asked for HOME; presentation mode alone must never skip navigation. */
    private suspend fun exitSessionIntervention(request: LimitInterventionRequest) {
        if (activeInterventionRequest != request) return
        try {
            val health = permissionHealthManager.refresh()
            val confirmed = health.canForceHome && coordinator.requestSessionHome(request.packageName)
            if (!confirmed) {
                withContext(Dispatchers.Main.immediate) { interventionOverlayController.openHomeScreen() }
            }
            currentForegroundApp = null
            finishTrackedSession(keepIntervention = true)
            Log.d(INTERVENTION_TAG, "session exit package=${request.packageName} " +
                "homeConfirmed=$confirmed launcherIntentRequested=${!confirmed}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "session HOME failed; user can use the system HOME gesture", error)
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(this@UsageMonitorService, "无法返回桌面，请使用系统主页手势", Toast.LENGTH_SHORT).show()
            }
        } finally {
            dismissActiveIntervention()
        }
    }

    private suspend fun resolveForegroundApp(packageName: String): ForegroundApp = withContext(Dispatchers.IO) {
        val info = InstalledAppInfoProvider(packageManager).getUserInstalledApp(packageName)
        ForegroundApp(packageName, info?.appName ?: packageName)
    }

    private fun observeInterventionCommands() {
        interventionCommandsJob = serviceScope.launch {
            coordinator.monitorCommands.collect { command ->
                try {
                    timingMutex.withLock {
                        when (command) {
                            is MonitorInterventionCommand.CheckEntry -> {
                                if (!command.result.isActive) return@withLock
                                val entry = command.entry
                                if (!coordinator.isCurrent(entry) || !trackedAppsLoaded || !limitsLoaded ||
                                    entry.packageName !in trackedPackageNames
                                ) {
                                    command.result.complete(EntryCheckResult.UNAVAILABLE)
                                    return@withLock
                                }
                                if (coordinator.isFocusBlocking(entry.packageName)) {
                                    command.result.complete(EntryCheckResult.HANDLED)
                                    return@withLock
                                }
                                val (limit, evaluation) = refreshDailySnapshot(entry.packageName)
                                if (!command.result.isActive || !coordinator.isCurrent(entry)) return@withLock
                                if (limit != null && evaluation?.isLimitReached == true) {
                                    friction.cancelBypass(entry.packageName)
                                    val app = resolveForegroundApp(entry.packageName)
                                    if (activeInterventionType == InterventionType.DAILY_LIMIT_REACHED &&
                                        activeInterventionPackageName == entry.packageName
                                    ) {
                                        goHomeAndFinish(entry.packageName)
                                    } else {
                                        handleInterventionDecision(interventionManager.evaluate(
                                            app.appName, evaluation, limit.baseLimitSeconds, 0L, isNewSession = true,
                                        ))
                                    }
                                    command.result.complete(
                                        if (coordinator.isObservedForeground(entry.packageName)) EntryCheckResult.UNAVAILABLE
                                        else EntryCheckResult.HANDLED,
                                    )
                                } else {
                                    if (activeInterventionType == InterventionType.DAILY_LIMIT_REACHED &&
                                        activeInterventionPackageName == entry.packageName
                                    ) dismissActiveIntervention()
                                    command.result.complete(EntryCheckResult.CHECK_FRICTION)
                                }
                            }
                            is MonitorInterventionCommand.AcceptedEntry -> {
                                if (coordinator.isCurrent(command.entry)) {
                                    val app = resolveForegroundApp(command.entry.packageName)
                                    currentForegroundApp = app
                                    processForegroundApp(app)
                                }
                            }
                            is MonitorInterventionCommand.ShowReentryGate -> {
                                if (!coordinator.isCurrent(command.entry)) return@withLock
                                // A limit edit/crossing may have happened after the first entry check.
                                val (limit, evaluation) = refreshDailySnapshot(command.entry.packageName)
                                val app = resolveForegroundApp(command.entry.packageName)
                                if (!coordinator.isCurrent(command.entry)) return@withLock
                                if (limit != null && evaluation?.isLimitReached == true) {
                                    friction.cancelBypass(command.entry.packageName)
                                    handleInterventionDecision(interventionManager.evaluate(
                                        app.appName, evaluation, limit.baseLimitSeconds, 0L, isNewSession = true,
                                    ))
                                    if (coordinator.isCurrent(command.entry)) coordinator.failOpenVisit(command.entry)
                                } else {
                                    if (!friction.isReentryGateArmed(app.packageName)) {
                                        coordinator.allowEntry(command.entry)
                                        return@withLock
                                    }
                                    val shown = showSessionGate(app, SessionGatePhase.REENTRY,
                                        coordinator.dailySnapshot(app.packageName)?.usedSeconds ?: 0L)
                                    if (!shown && coordinator.isCurrent(command.entry)) coordinator.failOpenVisit(command.entry)
                                }
                            }
                            is MonitorInterventionCommand.ForegroundLeft -> {
                                if (timedForegroundApp?.packageName == command.packageName) finishTrackedSession()
                                if (currentForegroundApp?.packageName == command.packageName) currentForegroundApp = null
                            }
                            is MonitorInterventionCommand.DismissForFocus -> {
                                if (!command.result.isActive) return@withLock
                                dismissActiveIntervention()
                                command.result.complete(true)
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (command is MonitorInterventionCommand.CheckEntry) command.result.complete(EntryCheckResult.UNAVAILABLE)
                    if (command is MonitorInterventionCommand.DismissForFocus) command.result.complete(false)
                    val entry = when (command) {
                        is MonitorInterventionCommand.AcceptedEntry -> command.entry
                        is MonitorInterventionCommand.ShowReentryGate -> command.entry
                        else -> null
                    }
                    if (entry != null) {
                        friction.failOpen(entry.packageName)
                        if (command is MonitorInterventionCommand.ShowReentryGate) {
                            coordinator.failOpenVisit(entry)
                        } else {
                            coordinator.suppressCurrentVisit(entry.packageName)
                        }
                        timingMutex.withLock {
                            dismissActiveIntervention()
                            coordinator.releaseOverlay(InterventionPriority.SESSION, entry.packageName)
                            coordinator.releaseOverlay(InterventionPriority.DAILY, entry.packageName)
                        }
                    }
                    Log.e(TAG, "intervention failed but monitoring continues", error)
                }
            }
        }
    }

    private suspend fun updateIdleNotificationIfNeeded(force: Boolean = false) {
        val usageDayKey = usageDayProvider.currentUsageDay().key
        if (
            !force &&
            lastIdleUsageDayKey == usageDayKey &&
            lastIdleTrackedPackages == trackedPackageNames
        ) {
            return
        }

        val totalUsageSeconds = usageTimeRepository.getTotalTodayUsageSeconds(
            trackedPackageNames,
        )
        notificationHelper.updateWaitingNotification(totalUsageSeconds)
        lastIdleUsageDayKey = usageDayKey
        lastIdleTrackedPackages = trackedPackageNames.toSet()
    }

    private fun handleDetectedApp(detectedApp: ForegroundApp) {
        val previousApp = currentForegroundApp
        if (previousApp?.packageName == detectedApp.packageName) return
        // A real app switch is a capability boundary, not a per-second permission scan.
        permissionHealthManager.refresh()

        if (previousApp == null) {
            Log.d(TAG, "当前应用: ${detectedApp.appName}")
        } else {
            Log.d(TAG, "切换: ${previousApp.appName} -> ${detectedApp.appName}")
        }
        Log.d(TAG, "packageName=${detectedApp.packageName}")
        currentForegroundApp = detectedApp
    }

    private fun startAsForegroundService() {
        val notification = notificationHelper.buildRunningNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationIds.FOREGROUND_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationIds.FOREGROUND_SERVICE, notification)
        }
        Log.d(FOREGROUND_NOTIFICATION_TAG, "foreground notification active serviceStarted=true")
    }

    private companion object {
        const val TAG = "UsageMonitorService"
        const val INTERVENTION_TAG = "InterventionType"
        const val SESSION_TRACKER_TAG = "SessionTracker"
        const val SECONDS_PER_MINUTE = 60L
        const val DETECTION_INTERVAL_MILLIS = 1_000L
        const val PERMISSION_REFRESH_INTERVAL_MILLIS = 5_000L
        const val DIAGNOSTIC_LOG_INTERVAL_SECONDS = 15L
        const val EVENT_OVERLAP_MILLIS = 500L
        const val INITIAL_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
        const val FOREGROUND_NOTIFICATION_TAG = "ForegroundNotification"
        const val USAGE_LIMIT_EVALUATOR_TAG = "UsageLimitEvaluator"
    }
}

internal fun selectInterventionType(
    dailyLimitTriggered: Boolean,
    sessionDue: Boolean,
    overlayShowing: Boolean,
): InterventionType? = when {
    dailyLimitTriggered -> InterventionType.DAILY_LIMIT_REACHED
    sessionDue && !overlayShowing -> InterventionType.SESSION_REMINDER
    else -> null
}
