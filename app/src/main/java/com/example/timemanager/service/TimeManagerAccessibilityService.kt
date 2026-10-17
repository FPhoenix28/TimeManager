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
import com.example.timemanager.config.InterventionRuntime
import com.example.timemanager.BuildConfig
import com.example.timemanager.permissions.PermissionHealthManager
import com.example.timemanager.domain.intervention.EntryCheckResult
import com.example.timemanager.domain.intervention.InterventionPriority
import kotlinx.coroutines.CancellationException
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
    private val coordinator = InterventionRuntime.coordinator
    private var homeCommandsJob: Job? = null
    private var focusStateJob: Job? = null
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
                    coordinator.onForegroundChanged(null)
                    deepFocusOverlaySuspended = true
                    if (::deepFocusOverlay.isInitialized) hideDeepFocusOverlay()
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
        observeHomeCommands()
        coordinator.setAccessibilityConnected(true, launcherPackage = deepFocusEssentials.launcherPackage)
        PermissionHealthManager.get(applicationContext).refresh()
        Log.d(
            TAG,
            "connected launcher=${deepFocusEssentials.launcherPackage} " +
                "ime=${deepFocusEssentials.inputMethodPackage} " +
                "dialer=${deepFocusEssentials.defaultDialerPackage} " +
                "callUi=${deepFocusEssentials.callUiPackages} " +
                "settings=${deepFocusEssentials.settingsPackage}",
        )
        focusStateJob?.cancel()
        focusStateJob = serviceScope.launch {
            repository.observeState().collect { state ->
                focusState = state
                coordinator.updateFocus(state)
                expiryJob?.cancel()
                when {
                    state.isActive() -> {
                        hideDeepFocusOverlay()
                        deepFocusNotification.cancel()
                        deepFocusOverlaySuspended = false
                        Log.d(TAG, "mode=${state.mode} start=${state.startTimeMillis} end=${state.endTimeMillis} blockedPackages=${state.blockedPackages}")
                        scheduleExpiry(state)
                    }
                    state.isDeepFocusActive() -> {
                        dismissFocusOverlay()
                        Log.d(TAG, "mode=${state.mode} status=${state.status} start=${state.startTimeMillis} end=${state.endTimeMillis}")
                        scheduleExpiry(state)
                        showDeepFocusOverlayIfAllowed(state)
                        deepFocusNotification.show(state)
                    }
                    state.isDeepFocusCompleted() -> {
                        dismissFocusOverlay()
                        Log.d(TAG, "mode=${state.mode} status=${state.status} enforcement=false")
                        showDeepFocusOverlayIfAllowed(state)
                        deepFocusNotification.show(state)
                    }
                    else -> {
                        dismissFocusOverlay()
                        hideDeepFocusOverlay()
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
        val foregroundPackage = event.packageName?.toString() ?: return
        val foregroundClass = event.className?.toString()
        // Own overlay and transient system windows are not app-exit/entry boundaries.
        val transient = foregroundPackage == packageName && foregroundClass != "$packageName.MainActivity" ||
            foregroundPackage == deepFocusEssentials.inputMethodPackage ||
            foregroundPackage in deepFocusEssentials.systemUiPackages
        val previousStable = coordinator.stableForegroundPackage()
        val stableWindow = !transient &&
            (event.isFullScreen || foregroundPackage == deepFocusEssentials.launcherPackage)
        if (stableWindow && coordinator.shouldIgnoreHomeEcho(foregroundPackage)) {
            logFrictionTransition(foregroundPackage, previousStable, "AWAITING_STABLE_EXIT")
            return
        }
        val entry = coordinator.onForegroundWindow(foregroundPackage, stableWindow)
        logFrictionTransition(foregroundPackage, previousStable, when {
            transient || !stableWindow -> "IGNORE_TRANSIENT_WINDOW"
            previousStable == foregroundPackage -> "IGNORE_SAME_PACKAGE_EVENT"
            entry != null -> "CHECK_REAL_REENTRY"
            else -> "STABLE_EXTERNAL_EXIT"
        })
        val now = System.currentTimeMillis()
        val state = focusState
        if (state.mode == FocusModeType.DEEP_FOCUS) {
            handleDeepFocusEvent(event, state, now)
            if (entry != null && coordinator.isCurrent(entry)) {
                if (state.isDeepFocusActive(now)) coordinator.allowEntry(entry)
                else enforceFrictionEntry(entry)
            }
            return
        }
        if (!state.isActive(now)) {
            if (state.mode == FocusModeType.FOCUS && now >= state.endTimeMillis) {
                serviceScope.launch { repository.stopIfExpired(now) }
            }
            entry?.let(::enforceFrictionEntry)
            return
        }
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
        if (!action.performHome) {
            if (!shouldBlock) entry?.let(::enforceFrictionEntry)
            return
        }
        lastBlockedPackage = foregroundPackage
        lastBlockedAt = elapsed

        val wentHome = performGlobalAction(GLOBAL_ACTION_HOME)
        if (wentHome) {
            coordinator.onHomeSucceeded(foregroundPackage)
        }
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
            if (!coordinator.dismissLowerForFocus() ||
                !coordinator.claimOverlay(InterventionPriority.FOCUS, packageName)
            ) return@launch
            val shown = overlay.show(appName, focusState.endTimeMillis) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                dismissFocusOverlay()
            }
            if (!shown) coordinator.releaseOverlay(InterventionPriority.FOCUS, packageName)
            Log.d(TAG, "foreground=$foregroundPackage overlayShown=$shown")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        coordinator.setAccessibilityConnected(false)
        PermissionHealthManager.get(applicationContext).refresh()
        homeCommandsJob?.cancel()
        focusStateJob?.cancel()
        expiryJob?.cancel()
        if (::overlay.isInitialized) dismissFocusOverlay()
        if (::deepFocusOverlay.isInitialized) hideDeepFocusOverlay()
        if (::deepFocusNotification.isInitialized) deepFocusNotification.cancel()
        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        coordinator.setAccessibilityConnected(false)
        PermissionHealthManager.get(applicationContext).refresh()
        homeCommandsJob?.cancel()
        focusStateJob?.cancel()
        expiryJob?.cancel()
        if (::overlay.isInitialized) dismissFocusOverlay()
        if (::deepFocusOverlay.isInitialized) hideDeepFocusOverlay()
        // Ordinary Session/Daily overlays belong to monitoring and survive this disconnect.
        return super.onUnbind(intent)
    }

    private fun observeHomeCommands() {
        homeCommandsJob?.cancel()
        homeCommandsJob = serviceScope.launch {
            coordinator.accessibilityCommands.collect { command ->
                if (!command.result.isActive) return@collect
                coordinator.markHomeRequested(command.packageName)
                val targetObserved = if (command.confirmLauncher) {
                    coordinator.stableForegroundPackage() == command.packageName
                } else coordinator.isObservedForeground(command.packageName)
                val success = targetObserved &&
                    performGlobalAction(GLOBAL_ACTION_HOME)
                if (success && !command.confirmLauncher) {
                    coordinator.onHomeSucceeded(command.packageName)
                } else if (!success) {
                    coordinator.cancelHomeRequest(command.packageName)
                }
                // Session requests are confirmed by a real launcher event, not this acknowledgement.
                Log.d("SessionFriction", "HOME package=${command.packageName} requested=$success " +
                    "awaitLauncher=${command.confirmLauncher}")
                command.result.complete(success)
            }
        }
    }

    private fun enforceFrictionEntry(entry: com.example.timemanager.domain.intervention.ForegroundEntry) {
        serviceScope.launch {
            try {
                when (coordinator.checkEntry(entry)) {
                    EntryCheckResult.HANDLED -> Unit
                    EntryCheckResult.UNAVAILABLE -> coordinator.failOpenVisit(entry)
                    EntryCheckResult.CHECK_FRICTION -> {
                        if (!coordinator.isCurrent(entry) || coordinator.isFocusBlocking(entry.packageName)) return@launch
                        if (coordinator.entryPriority(entry.packageName) == InterventionPriority.DAILY) {
                            // A fresh evaluator snapshot can outrank admission between coroutine steps.
                            coordinator.requestReentryGate(entry)
                            return@launch
                        }
                        val consumed = coordinator.friction.consumeBypass(entry.packageName)
                        val admitted = consumed || coordinator.friction.isEntryAdmitted(entry.packageName)
                        if (admitted || !coordinator.friction.isReentryGateArmed(entry.packageName)) {
                            coordinator.allowEntry(entry)
                            logFrictionTransition(entry.packageName, entry.packageName, "ALLOW_CONSUMED_OR_UNARMED_STAGE")
                        } else {
                            logFrictionTransition(entry.packageName, entry.packageName, "REQUEST_REENTRY_GATE")
                            coordinator.requestReentryGate(entry)
                        }
                        Log.d("SessionFriction", "entry package=${entry.packageName} bypassConsumed=$consumed admitted=$admitted")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                coordinator.failOpenVisit(entry)
                Log.e(TAG, "friction entry failed; allowing this visit", error)
            }
        }
    }

    private fun logFrictionTransition(eventPackage: String, previousStable: String?, action: String) {
        if (!BuildConfig.DEBUG) return
        val currentStable = coordinator.stableForegroundPackage()
        val pkg = if (coordinator.friction.snapshot(eventPackage) != null) eventPackage else previousStable
        val state = pkg?.let(coordinator.friction::snapshot)
        Log.d("SessionFrictionTransition", "package=$pkg eventPackage=$eventPackage " +
            "previousStablePackage=$previousStable currentStablePackage=$currentStable " +
            "reminderStage=${state?.reminderCount ?: 0} gateArmed=${state?.reentryGateArmed ?: false} " +
            "gateConsumed=${state?.reentryGateConsumed ?: false} " +
            "state=${state?.pendingGate?.phase ?: if (state?.isActive == true) "FRICTION" else "NORMAL"} " +
            "awaitingStableExit=${pkg?.let(coordinator::awaitingStableExit) ?: false} action=$action")
    }

    private fun hideDeepFocusOverlay() {
        deepFocusOverlay.hide()
        coordinator.releaseOverlay(InterventionPriority.DEEP, packageName)
    }

    private fun dismissFocusOverlay() {
        overlay.dismiss()
        coordinator.releaseOverlay(InterventionPriority.FOCUS, packageName)
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
        if (!coordinator.dismissLowerForFocus() ||
            !coordinator.claimOverlay(InterventionPriority.DEEP, packageName)
        ) return
        val shown = deepFocusOverlay.showOrUpdate(
            state = state,
            accessibilityEnabled = true,
            onEndConfirmed = { serviceScope.launch { repository.stopFocus() } },
            onCompleted = { serviceScope.launch { repository.stopFocus() } },
        )
        if (!shown) coordinator.releaseOverlay(InterventionPriority.DEEP, packageName)
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
                hideDeepFocusOverlay()
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
                    if (wentHome) {
                        coordinator.onHomeSucceeded(foregroundPackage)
                    }
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
