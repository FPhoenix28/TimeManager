package com.example.timemanager.ui.focus

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.ui.intervention.OverlayComposeHostOwner
import com.example.timemanager.ui.theme.TimeManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeepFocusOverlayController(context: Context) {
    private val context = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: ComposeView? = null
    private var hostOwner: OverlayComposeHostOwner? = null
    private var focusState by mutableStateOf(FocusModeState())
    private var accessibilityEnabled by mutableStateOf(true)
    private var onEndConfirmed: () -> Unit = {}
    private var onCompleted: () -> Unit = {}

    val isShowing: Boolean get() = overlayView?.isAttachedToWindow == true

    suspend fun showOrUpdate(
        state: FocusModeState,
        accessibilityEnabled: Boolean,
        onEndConfirmed: () -> Unit,
        onCompleted: () -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        focusState = state
        this@DeepFocusOverlayController.accessibilityEnabled = accessibilityEnabled
        this@DeepFocusOverlayController.onEndConfirmed = onEndConfirmed
        this@DeepFocusOverlayController.onCompleted = onCompleted
        if (isShowing) return@withContext true
        if (overlayView != null) hide()
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "show skipped: overlay permission missing")
            return@withContext false
        }

        var owner: OverlayComposeHostOwner? = null
        var view: ComposeView? = null
        try {
            owner = OverlayComposeHostOwner().also { it.resume() }
            view = ComposeView(context).apply {
                owner.installViewTreeOwners(this)
                setContent {
                    TimeManagerTheme {
                        DeepFocusContent(
                            focus = focusState,
                            accessibilityEnabled = this@DeepFocusOverlayController.accessibilityEnabled,
                            overlayEnabled = true,
                            onEndConfirmed = { this@DeepFocusOverlayController.onEndConfirmed() },
                            onCompleted = { this@DeepFocusOverlayController.onCompleted() },
                        )
                    }
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.FILL }
            windowManager.addView(view, params)
            overlayView = view
            hostOwner = owner
            Log.d(TAG, "overlay attached status=${state.status}")
            true
        } catch (error: Exception) {
            Log.e(TAG, "overlay failed", error)
            view?.let {
                if (it.isAttachedToWindow) windowManager.removeViewImmediate(it)
                it.disposeComposition()
            }
            owner?.destroy()
            false
        }
    }

    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "overlay already detached", error)
        }
        view.disposeComposition()
        hostOwner?.destroy()
        hostOwner = null
        onEndConfirmed = {}
        onCompleted = {}
        Log.d(TAG, "overlay dismissed")
    }

    private companion object {
        const val TAG = "DeepFocusOverlay"
    }
}
