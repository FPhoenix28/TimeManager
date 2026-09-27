package com.example.timemanager.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.preferences.FocusModePreferences
import com.example.timemanager.data.repository.FocusModeRepository
import com.example.timemanager.domain.focus.DeepFocusDisposition
import com.example.timemanager.domain.focus.DeepFocusEnforcementPolicy
import com.example.timemanager.domain.focus.DeepFocusEssentialAppResolver
import com.example.timemanager.domain.focus.DeepFocusEssentialApps
import com.example.timemanager.domain.focus.FocusEnforcementActionPolicy
import com.example.timemanager.domain.focus.FocusEnforcementPolicy
import com.example.timemanager.notifications.DeepFocusNotificationHelper
import com.example.timemanager.ui.focus.DeepFocusOverlayController
import com.example.timemanager.ui.focus.FocusBlockedOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Event-driven Focus enforcement only; usage and limit monitoring stay in UsageMonitorService. */
class TimeManagerAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: FocusModeRepository
    private lateinit var overlay: FocusBlockedOverlayController
    private lateinit var deepFocusOverlay: DeepFocusOverlayController
    private lateinit var deepFocusEssentials: DeepFocusEssentialApps
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var deepFocusNotification: DeepFocusNotificationHelper
    private var focusState = FocusModeState()
    private var expiryJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L
    private var lastObservedPackage: String? = null
    private var deepFocusOverlaySuspended = false
    private var screenReceiverRegistered = false
    private val appNameCache = mutableMapOf<String, String>()
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    deepFocusOverlaySuspended = true
                    if (::deepFocusOverlay.isInitialized) deepFocusOverlay.hide()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!keyguardManager.isKeyguardLocked) {
                        restoreDeepFocusOverlayAfterSystemUi()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    restoreDeepFocusOverlayAfterSystemUi()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = FocusModeRepository(FocusModePreferences(this), packageName)
        overlay = FocusBlockedOverlayController(this)
        deepFocusOverlay = DeepFocusOverlayController(this)
        deepFocusEssentials = DeepFocusEssentialAppResolver(this).resolve()
        keyguardManager = getSystemService(KeyguardManager::class.java)
        deepFocusNotification = DeepFocusNotificationHelper(this)
        registerScreenStateReceiver()
        Log.d(
            TAG,
            "connected launcher=${deepFocusEssentials.launcherPackage} " +
                "ime=${deepFocusEssentials.inputMethodPackage} " +
                "dialer=${deepFocusEssentials.defaultDialerPackage} " +
                "callUi=${deepFocusEssentials.callUiPackages} " +
                "settings=${deepFocusEssentials.settingsPackage}",
        )
        serviceScope.launch {
            repository.observeState().collect { state ->
                focusState = state
                expiryJob?.cancel()
                when {
                    state.isActive() -> {
                        deepFocusOverlay.hide()
                        deepFocusNotification.cancel()
                        deepFocusOverlaySuspended = false
                        Log.d(TAG, "mode=${state.mode} start=${state.startTimeMillis} end=${state.endTimeMillis} blockedPackages=${state.blockedPackages}")
                        scheduleExpiry(state)
                    }
                    state.isDeepFocusActive() -> {
                        overlay.dismiss()
                        Log.d(TAG, "mode=${state.mode} status=${state.status} start=${state.startTimeMillis} end=${state.endTimeMillis}")
                        scheduleExpiry(state)
                        showDeepFocusOverlayIfAllowed(state)
                        deepFocusNotification.show(state)
                    }
                    state.isDeepFocusCompleted() -> {
                        overlay.dismiss()
                        Log.d(TAG, "mode=${state.mode} status=${state.status} enforcement=false")
                        showDeepFocusOverlayIfAllowed(state)
                        deepFocusNotification.show(state)
                    }
                    else -> {
                        overlay.dismiss()
                        deepFocusOverlay.hide()
                        deepFocusNotification.cancel()
                        deepFocusOverlaySuspended = false
                        if (state.mode != FocusModeType.OFF) repository.stopIfExpired()
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val now = System.currentTimeMillis()
        val state = focusState
        if (state.mode == FocusModeType.DEEP_FOCUS) {
            handleDeepFocusEvent(event, state, now)
            return
        }
        if (!state.isActive(now)) {
            if (state.mode == FocusModeType.FOCUS && now >= state.endTimeMillis) {
                serviceScope.launch { repository.stopIfExpired(now) }
            }
            return
        }
        val foregroundPackage = event.packageName?.toString() ?: return
        val shouldBlock = FocusEnforcementPolicy.shouldBlock(
            state = state,
            foregroundPackage = foregroundPackage,
            ownPackage = packageName,
            nowMillis = now,
        )
        if (state.isActive(now) && foregroundPackage != lastObservedPackage) {
            Log.d(TAG, "foreground=$foregroundPackage mode=${state.mode} blocked=$shouldBlock")
            lastObservedPackage = foregroundPackage
        }
        val elapsed = SystemClock.elapsedRealtime()
        val action = FocusEnforcementActionPolicy.decide(
            shouldBlock = shouldBlock,
            overlayShowing = overlay.isShowing,
            debounced = foregroundPackage == lastBlockedPackage &&
                elapsed - lastBlockedAt < DEBOUNCE_MILLIS,
        )
        if (!action.performHome) return
        lastBlockedPackage = foregroundPackage
        lastBlockedAt = elapsed

        val wentHome = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(TAG, "foreground=$foregroundPackage mode=${state.mode} blocked=true action=HOME success=$wentHome")
        if (!action.showBlockedOverlay) return
        serviceScope.launch {
            val appName = appNameCache[foregroundPackage] ?: withContext(Dispatchers.IO) {
                runCatching {
                    val info = packageManager.getApplicationInfo(foregroundPackage, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(foregroundPackage)
            }.also { appNameCache[foregroundPackage] = it }
            if (!FocusEnforcementPolicy.shouldBlock(
                    focusState,
                    foregroundPackage,
                    packageName,
                    System.currentTimeMillis(),
                )
            ) return@launch
            val shown = overlay.show(appName, focusState.endTimeMillis) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                overlay.dismiss()
            }
            Log.d(TAG, "foreground=$foregroundPackage overlayShown=$shown")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        expiryJob?.cancel()
        if (::overlay.isInitialized) overlay.dismiss()
        if (::deepFocusOverlay.isInitialized) deepFocusOverlay.hide()
        if (::deepFocusNotification.isInitialized) deepFocusNotification.cancel()
        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun scheduleExpiry(state: FocusModeState) {
        expiryJob = serviceScope.launch {
            delay(state.remainingMillis())
            repository.stopIfExpired()
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun restoreDeepFocusOverlayAfterSystemUi() {
        deepFocusOverlaySuspended = false
        val state = focusState
        if (state.isDeepFocusActive() || state.isDeepFocusCompleted()) {
            serviceScope.launch { showDeepFocusOverlayIfAllowed(state) }
        }
    }

    private suspend fun showDeepFocusOverlayIfAllowed(state: FocusModeState) {
        if (deepFocusOverlaySuspended) return
        val shown = deepFocusOverlay.showOrUpdate(
            state = state,
            accessibilityEnabled = true,
            onEndConfirmed = { serviceScope.launch { repository.stopFocus() } },
            onCompleted = { serviceScope.launch { repository.stopFocus() } },
        )
        Log.d(TAG, "deepOverlayRequested shown=$shown status=${state.status}")
    }

    private fun handleDeepFocusEvent(
        event: AccessibilityEvent,
        state: FocusModeState,
        nowMillis: Long,
    ) {
        if (!state.isDeepFocusActive(nowMillis) && !state.isDeepFocusCompleted()) {
            if (nowMillis >= state.endTimeMillis) {
                serviceScope.launch { repository.stopIfExpired(nowMillis) }
            }
            return
        }
        val foregroundPackage = event.packageName?.toString() ?: return
        val foregroundClass = event.className?.toString()
        val disposition = DeepFocusEnforcementPolicy.evaluate(
            state = state,
            foregroundPackage = foregroundPackage,
            foregroundClass = foregroundClass,
            essentials = deepFocusEssentials,
            keyguardLocked = keyguardManager.isKeyguardLocked,
            overlayPermissionGranted = Settings.canDrawOverlays(this),
            nowMillis = nowMillis,
        )
        Log.d(
            TAG,
            "deep package=$foregroundPackage class=$foregroundClass disposition=$disposition " +
                "status=${state.status} remaining=${state.remainingMillis(nowMillis)}",
        )
        when (disposition) {
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY -> {
                deepFocusOverlaySuspended = true
                deepFocusOverlay.hide()
            }
            DeepFocusDisposition.ALLOW -> {
                val wasSuspended = deepFocusOverlaySuspended
                if (
                    wasSuspended &&
                    !DeepFocusEnforcementPolicy.shouldResumeOverlayAfterSuspension(
                        foregroundPackage,
                        deepFocusEssentials,
                    )
                ) {
                    return
                }
                deepFocusOverlaySuspended = false
                if (wasSuspended || !deepFocusOverlay.isShowing) {
                    serviceScope.launch { showDeepFocusOverlayIfAllowed(focusState) }
                }
            }
            DeepFocusDisposition.BLOCK -> {
                deepFocusOverlaySuspended = false
                val elapsed = SystemClock.elapsedRealtime()
                val duplicate = foregroundPackage == lastBlockedPackage &&
                    elapsed - lastBlockedAt < DEBOUNCE_MILLIS
                if (!duplicate) {
                    lastBlockedPackage = foregroundPackage
                    lastBlockedAt = elapsed
                    val wentHome = performGlobalAction(GLOBAL_ACTION_HOME)
                    Log.d(TAG, "deep blocked=$foregroundPackage action=HOME success=$wentHome")
                }
                serviceScope.launch { showDeepFocusOverlayIfAllowed(focusState) }
            }
        }
    }

    private companion object {
        const val TAG = "FocusEnforcement"
        const val DEBOUNCE_MILLIS = 700L
    }
}
