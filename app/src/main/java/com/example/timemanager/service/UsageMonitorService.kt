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
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.preferences.TrackedAppPreferences
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.monitor.ForegroundAppDetector
import com.example.timemanager.monitor.SessionTracker
import com.example.timemanager.notifications.MonitorNotificationHelper
import com.example.timemanager.notifications.NotificationHelper
import com.example.timemanager.notifications.UsageLimitNotifier
import com.example.timemanager.timer.AppTimer
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
                    if (timedForegroundApp != null) {
                        finishTrackedSession()
                    } else {
                        updateIdleNotificationIfNeeded()
                    }
                } else {
                    currentForegroundApp?.let { foregroundApp ->
                        processForegroundApp(foregroundApp)
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
        val limitEvaluation = limitMinutesByPackage[foregroundApp.packageName]?.let { limitMinutes ->
            limitEvaluator.evaluate(
                packageName = foregroundApp.packageName,
                todayUsageSeconds = todayUsageSeconds,
                limitMinutes = limitMinutes,
            )
        }
        if (limitEvaluation != null) {
            usageLimitNotifier.notifyIfNeeded(foregroundApp.appName, limitEvaluation)
        }
        notificationHelper.updateRunningNotification(
            timerState = timerState,
            todayUsageSeconds = todayUsageSeconds,
            limitEvaluation = limitEvaluation,
        )
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
            usageTimeRepository.recordTimerState(finalTimerState)
            usageTimeRepository.flush()
            Log.d(TAG, "Stopped timing untracked app transition: ${finalTimerState.appName}")
        }
        timedForegroundApp = null
        sessionTracker.endSession()
        updateIdleNotificationIfNeeded(force = true)
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
                MonitorNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(MonitorNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val TAG = "UsageMonitorService"
        const val DETECTION_INTERVAL_MILLIS = 1_000L
        const val EVENT_OVERLAP_MILLIS = 500L
        const val INITIAL_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
