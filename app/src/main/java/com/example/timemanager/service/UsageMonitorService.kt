package com.example.timemanager.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.timemanager.data.model.ForegroundApp
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.monitor.ForegroundAppDetector
import com.example.timemanager.notifications.MonitorNotificationHelper
import com.example.timemanager.timer.AppTimer
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
    private var detectionJob: Job? = null
    private var currentForegroundApp: ForegroundApp? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = MonitorNotificationHelper(applicationContext)
        startAsForegroundService()
        foregroundAppDetector = ForegroundAppDetector(applicationContext)
        appTimer = AppTimer()
        usageTimeRepository = UsageTimeRepository(
            usageRepository = UsageRepository(UsageStatsReader(applicationContext)),
            preferences = RealtimeUsagePreferences(applicationContext),
        )
        startDetection()
        Log.d(TAG, "Usage monitor service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startDetection()
        return START_STICKY
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        val finalTimerState = appTimer.finishCurrentSession()
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
                currentForegroundApp?.let { foregroundApp ->
                    val timerState = appTimer.update(foregroundApp)
                    val todayUsageSeconds = usageTimeRepository.recordTimerState(timerState)
                    notificationHelper.updateRunningNotification(
                        timerState = timerState,
                        todayUsageSeconds = todayUsageSeconds,
                    )
                }
                queryStartTime = now - EVENT_OVERLAP_MILLIS
                delay(DETECTION_INTERVAL_MILLIS)
            }
        }
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
