package com.example.timemanager.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.timemanager.data.model.ForegroundApp
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.preferences.InterventionPreferences
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.preferences.TrackedAppPreferences
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.data.model.LimitInterventionDecision
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

/** User-visible foreground service that owns the foreground-app monitoring loop. */
class UsageMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
    private lateinit var powerManager: PowerManager
    private var detectionJob: Job? = null
    private var trackedAppsJob: Job? = null
    private var limitsJob: Job? = null
    private var currentForegroundApp: ForegroundApp? = null
    private var timedForegroundApp: ForegroundApp? = null
    private var trackedPackageNames: Set<String> = emptySet()
    private var limitMinutesByPackage: Map<String, Int> = emptyMap()
    private var lastIdleUsageDayKey: String? = null
    private var lastIdleTrackedPackages: Set<String> = emptySet()
    private val timingMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = MonitorNotificationHelper(applicationContext)
        startAsForegroundService()
        foregroundAppDetector = ForegroundAppDetector(applicationContext)
        powerManager = getSystemService(PowerManager::class.java)
        appTimer = AppTimer()
        sessionTracker = SessionTracker()
        usageDayProvider = UsageDayProvider()
        usageTimeRepository = UsageTimeRepository(
            usageRepository = UsageRepository(UsageStatsReader(applicationContext)),
            preferences = RealtimeUsagePreferences(applicationContext),
            usageDayProvider = usageDayProvider,
        )
        trackedAppRepository = TrackedAppRepository(
            TrackedAppPreferences(applicationContext),
        )
        limitRepository = LimitRepository(AppLimitPreferences(applicationContext))
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
        )
        interventionOverlayController = InterventionOverlayController(applicationContext)
        observeTrackedApps()
        observeLimits()
        startDetection()
        Log.d(TAG, "Usage monitor service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startDetection()
        return START_STICKY
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        trackedAppsJob?.cancel()
        limitsJob?.cancel()
        val finalTimerState = appTimer.finishCurrentSession()
        sessionTracker.endSession()
        runBlocking(Dispatchers.IO) {
            finalTimerState?.let { usageTimeRepository.recordTimerState(it) }
            usageTimeRepository.flush()
        }
        runBlocking {
            interventionOverlayController.dismiss()
        }
        serviceScope.cancel()
        Log.d(TAG, "Usage monitor service stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDetection() {
        if (detectionJob?.isActive == true) return

        detectionJob = serviceScope.launch {
            var queryStartTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS

            while (isActive) {
                val now = System.currentTimeMillis()
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
                    currentForegroundApp?.let { foregroundApp ->
                        timingMutex.withLock {
                            processForegroundApp(foregroundApp)
                        }
                    }
                }
                queryStartTime = now - EVENT_OVERLAP_MILLIS
                delay(DETECTION_INTERVAL_MILLIS)
            }
        }
    }

    private fun observeTrackedApps() {
        if (trackedAppsJob?.isActive == true) return
        trackedAppsJob = serviceScope.launch {
            trackedAppRepository.observeTrackedPackageNames().collect { packageNames ->
                trackedPackageNames = packageNames
                timingMutex.withLock {
                    val timedApp = timedForegroundApp
                    if (timedApp != null && timedApp.packageName !in packageNames) {
                        finishTrackedSession()
                    }
                }
                Log.d(TAG, "Tracked apps updated: ${packageNames.size}")
            }
        }
    }

    private suspend fun processForegroundApp(foregroundApp: ForegroundApp) {
        if (foregroundApp.packageName !in trackedPackageNames) {
            if (timedForegroundApp != null) {
                finishTrackedSession()
            } else {
                updateIdleNotificationIfNeeded()
            }
            return
        }

        val previousTimedApp = timedForegroundApp
        val isNewSession = previousTimedApp?.packageName != foregroundApp.packageName
        if (previousTimedApp != null && isNewSession) {
            if (interventionManager.onAppLeft(previousTimedApp.packageName)) {
                interventionOverlayController.dismiss()
            }
        }
        if (isNewSession) {
            interventionManager.onManagedAppEntered(foregroundApp.packageName)
        }
        val timerState = appTimer.update(foregroundApp)
        sessionTracker.update(timerState)?.let { reminder ->
            alertNotificationHelper.sendSessionReminder(
                packageName = foregroundApp.packageName,
                appName = foregroundApp.appName,
                reminder = reminder,
            )
        }
        timedForegroundApp = foregroundApp
        lastIdleUsageDayKey = null
        lastIdleTrackedPackages = emptySet()
        val todayUsageSeconds = usageTimeRepository.recordTimerState(timerState)
        val baseLimitMinutes = limitMinutesByPackage[foregroundApp.packageName]
        val effectiveLimit = baseLimitMinutes?.let { baseLimit ->
            interventionManager.getEffectiveLimit(
                packageName = foregroundApp.packageName,
                baseDailyLimitMinutes = baseLimit,
            )
        }
        val limitEvaluation = effectiveLimit?.let { limit ->
            limitEvaluator.evaluate(
                packageName = foregroundApp.packageName,
                todayUsageSeconds = todayUsageSeconds,
                limitMinutes = limit.effectiveLimitMinutes,
            )
        }
        if (limitEvaluation != null) {
            usageLimitNotifier.notifyIfNeeded(foregroundApp.appName, limitEvaluation)
        } else if (interventionManager.onAppLeft(foregroundApp.packageName)) {
            interventionOverlayController.dismiss()
        }
        val foregroundNotificationUpdated = notificationHelper.updateRunningNotification(
            timerState = timerState,
            todayUsageSeconds = todayUsageSeconds,
            limitEvaluation = limitEvaluation,
        )
        if (limitEvaluation != null) {
            Log.d(
                TAG,
                "packageName=${foregroundApp.packageName} managed=true " +
                    "todayUsageSeconds=$todayUsageSeconds " +
                    "baseLimitMinutes=${effectiveLimit?.baseDailyLimitMinutes} " +
                    "todayExtensionMinutes=${effectiveLimit?.todayExtensionMinutes} " +
                    "effectiveLimitSeconds=${limitEvaluation.limitSeconds} " +
                    "isOverLimit=${limitEvaluation.isLimitReached} " +
                    "foregroundServiceRunning=true " +
                    "foregroundNotificationUpdated=$foregroundNotificationUpdated",
            )
            val decision = interventionManager.evaluate(
                appName = foregroundApp.appName,
                evaluation = limitEvaluation,
                baseDailyLimitMinutes = checkNotNull(baseLimitMinutes),
                currentSessionSeconds = timerState.currentSessionSeconds,
                isNewSession = isNewSession,
            )
            try {
                handleInterventionDecision(decision)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failedPackageName = (decision as? LimitInterventionDecision.Show)
                    ?.request
                    ?.packageName
                failedPackageName?.let(interventionManager::onDisplayFailed)
                Log.e(TAG, "intervention failed but monitoring continues", error)
            }
        }
    }

    private fun observeLimits() {
        if (limitsJob?.isActive == true) return
        limitsJob = serviceScope.launch {
            limitRepository.observeAllLimits().collect { limits ->
                limitMinutesByPackage = limits.associate { limit ->
                    limit.packageName to limit.limitMinutes
                }
                Log.d(TAG, "App limits updated: ${limits.size}")
            }
        }
    }

    private suspend fun finishTrackedSession() {
        val finalTimerState = appTimer.finishCurrentSession()
        if (finalTimerState != null) {
            if (interventionManager.onAppLeft(finalTimerState.packageName)) {
                interventionOverlayController.dismiss()
            }
            usageTimeRepository.recordTimerState(finalTimerState)
            usageTimeRepository.flush()
            Log.d(TAG, "Stopped timing untracked app transition: ${finalTimerState.appName}")
        }
        timedForegroundApp = null
        sessionTracker.endSession()
        updateIdleNotificationIfNeeded(force = true)
    }

    private suspend fun handleInterventionDecision(decision: LimitInterventionDecision) {
        when (decision) {
            LimitInterventionDecision.None -> Unit
            LimitInterventionDecision.Hide -> interventionOverlayController.dismiss()
            is LimitInterventionDecision.Show -> {
                val request = decision.request
                val wasShown = interventionOverlayController.show(
                    request = request,
                    onExit = {
                        serviceScope.launch {
                            timingMutex.withLock {
                                interventionManager.closeVisibleIntervention(request.packageName)
                                interventionOverlayController.dismiss()
                                interventionOverlayController.openHomeScreen()
                            }
                        }
                    },
                    onContinue = {
                        serviceScope.launch {
                            timingMutex.withLock {
                                if (
                                    interventionManager.grantFiveMinuteExtension(
                                        request.packageName,
                                    )
                                ) {
                                    interventionOverlayController.dismiss()
                                    reevaluateAfterExtension(request.packageName)
                                }
                            }
                        }
                    },
                )
                if (!wasShown) {
                    interventionManager.onDisplayFailed(request.packageName)
                    Log.w(TAG, "intervention failed but monitoring continues")
                }
            }
        }
    }

    private suspend fun reevaluateAfterExtension(packageName: String) {
        val foregroundApp = timedForegroundApp
            ?.takeIf { it.packageName == packageName }
            ?: return
        val baseLimitMinutes = limitMinutesByPackage[packageName] ?: return
        val timerState = appTimer.update(foregroundApp)
        val todayUsageSeconds = usageTimeRepository.recordTimerState(timerState)
        val effectiveLimit = interventionManager.getEffectiveLimit(
            packageName = packageName,
            baseDailyLimitMinutes = baseLimitMinutes,
        )
        val evaluation = limitEvaluator.evaluate(
            packageName = packageName,
            todayUsageSeconds = todayUsageSeconds,
            limitMinutes = effectiveLimit.effectiveLimitMinutes,
        )
        notificationHelper.updateRunningNotification(
            timerState = timerState,
            todayUsageSeconds = todayUsageSeconds,
            limitEvaluation = evaluation,
        )
        Log.d(
            TAG,
            "limit extension reevaluated packageName=$packageName " +
                "todayUsageSeconds=$todayUsageSeconds " +
                "effectiveLimitSeconds=${evaluation.limitSeconds} " +
                "isOverLimit=${evaluation.isLimitReached}",
        )
        handleInterventionDecision(
            interventionManager.evaluate(
                appName = foregroundApp.appName,
                evaluation = evaluation,
                baseDailyLimitMinutes = baseLimitMinutes,
                currentSessionSeconds = timerState.currentSessionSeconds,
                isNewSession = false,
            ),
        )
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
        const val DETECTION_INTERVAL_MILLIS = 1_000L
        const val EVENT_OVERLAP_MILLIS = 500L
        const val INITIAL_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
        const val FOREGROUND_NOTIFICATION_TAG = "ForegroundNotification"
    }
}
