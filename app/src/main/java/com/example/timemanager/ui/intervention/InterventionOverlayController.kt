package com.example.timemanager.ui.intervention

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.timemanager.domain.todo.TodoRuntime
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.utils.OverlayPermissionManager
import com.example.timemanager.config.BuildTimeConfig
import com.example.timemanager.config.TimeConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Displays one full-screen Compose overlay above the currently used app. */
class InterventionOverlayController(
    context: Context,
    private val timingProvider: TimeConfigProvider = BuildTimeConfig.provider,
) {
    private val context = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: ComposeView? = null
    private var hostOwner: OverlayComposeHostOwner? = null
    private var isOverlayShowing = false

    fun isShowing(): Boolean = isOverlayShowing

    suspend fun show(
        request: LimitInterventionRequest,
        onExit: () -> Unit,
        onContinue: () -> Unit = {},
        onContinueWithExtension: ((Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        Log.d(TAG, "show requested packageName=${request.packageName}")
        if (isOverlayShowing) {
            Log.d(TAG, "overlay already showing; addView skipped")
            return@withContext false
        }
        if (request.type == InterventionType.SESSION_REMINDER && request.sessionGate == null) return@withContext false
        val permissionGranted = OverlayPermissionManager.canDrawOverlays(context)
        Log.d(TAG, "permission granted=$permissionGranted")
        if (!permissionGranted) return@withContext false

        var ownerToCleanUp: OverlayComposeHostOwner? = null
        var viewToCleanUp: ComposeView? = null
        try {
            val owner = OverlayComposeHostOwner().also { it.resume() }
            ownerToCleanUp = owner
            Log.d(TAG, "host created")
            val view = ComposeView(context).apply {
                owner.installViewTreeOwners(this)
                setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
                Log.d(TAG, "view tree owners installed")
                setContent {
                    TimeManagerTheme {
                        val todoSummary by TodoRuntime.get(context).summaryProvider.summary.collectAsState()
                        val timingFlow = remember(timingProvider) { timingProvider.observe() }
                        val timing by timingFlow.collectAsState(timingProvider.current())
                        val displayedRequest = request.copy(extensionDurationSeconds = timing.extensionDurationSeconds)
                        LimitReachedScreen(
                            request = displayedRequest,
                            onExit = onExit,
                            onContinue = {
                                if (request.type == InterventionType.DAILY_LIMIT_REACHED && onContinueWithExtension != null)
                                    onContinueWithExtension(displayedRequest.extensionDurationSeconds)
                                else onContinue()
                            },
                            todoSummary = todoSummary,
                        )
                    }
                }
            }
            viewToCleanUp = view
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            }

            Log.d(TAG, "addView requested")
            windowManager.addView(view, params)
            hostOwner = owner
            overlayView = view
            isOverlayShowing = true
            Log.d(TAG, "overlay attached")
            true
        } catch (error: Exception) {
            Log.e(TAG, "error while showing overlay", error)
            viewToCleanUp?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeViewImmediate(view)
                    } catch (removeError: IllegalArgumentException) {
                        Log.w(TAG, "failed overlay was already detached", removeError)
                    }
                }
                view.disposeComposition()
            }
            ownerToCleanUp?.destroy()
            false
        }
    }

    suspend fun dismiss() = withContext(Dispatchers.Main.immediate) {
        dismissOnMain()
    }

    fun openHomeScreen() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }

    /** Only invoked by the explicit, deadline-validated Continue action. */
    fun launchApplication(packageName: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "launch unavailable packageName=$packageName")
            false
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.d(TAG, "user-confirmed launch requested packageName=$packageName")
            true
        }
    } catch (error: Exception) {
        Log.e(TAG, "user-confirmed launch failed packageName=$packageName", error)
        false
    }

    private fun dismissOnMain() {
        if (!isOverlayShowing && overlayView == null && hostOwner == null) return
        Log.d(TAG, "overlay dismissed")
        isOverlayShowing = false
        hostOwner?.destroy()
        overlayView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "overlay view was already detached", error)
            }
            view.disposeComposition()
        }
        overlayView = null
        hostOwner = null
        Log.d(TAG, "overlay disposed")
    }

    private companion object {
        const val TAG = "OverlayIntervention"
    }
}
