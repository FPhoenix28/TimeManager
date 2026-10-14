package com.example.timemanager.ui.focus

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.example.timemanager.ui.intervention.OverlayComposeHostOwner
import com.example.timemanager.ui.intervention.OverlayDialogSize
import com.example.timemanager.ui.intervention.OverlayLayoutPolicy
import com.example.timemanager.ui.theme.TimeManagerTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Accessibility-only overlay, separate from the daily-limit intervention controller. */
class FocusBlockedOverlayController(context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val context = context.applicationContext
    private var overlayView: ComposeView? = null
    private var hostOwner: OverlayComposeHostOwner? = null
    private var maxContentHeight by mutableStateOf(0.dp)
    private var configurationRegistered = false
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // Remeasure the existing view after the display has applied the new configuration.
            val view = overlayView ?: return
            view.post {
                if (overlayView === view) updateWindowSize(view)
            }
        }

        override fun onLowMemory() = Unit
    }

    val isShowing: Boolean get() = overlayView != null

    suspend fun show(
        appName: String,
        endTimeMillis: Long,
        onReturn: () -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (isShowing) return@withContext true
        var owner: OverlayComposeHostOwner? = null
        var view: ComposeView? = null
        try {
            val dialogSize = currentDialogSize()
            maxContentHeight = dialogSize.maxHeightDp.dp
            owner = OverlayComposeHostOwner().also { it.resume() }
            view = ComposeView(context).apply {
                owner.installViewTreeOwners(this)
                setContent {
                    TimeManagerTheme {
                        FocusBlockedContent(
                            appName, endTimeMillis, onReturn,
                            modifier = Modifier.heightIn(max = maxContentHeight),
                        )
                    }
                }
            }
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                (dialogSize.widthDp * density).roundToInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0.32f
            }
            windowManager.addView(view, params)
            overlayView = view
            hostOwner = owner
            context.registerComponentCallbacks(configurationCallbacks)
            configurationRegistered = true
            Log.d(TAG, "Focus overlay attached")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Focus overlay failed", error)
            unregisterConfigurationCallback()
            overlayView = null
            hostOwner = null
            view?.let {
                if (it.isAttachedToWindow) windowManager.removeViewImmediate(it)
                it.disposeComposition()
            }
            owner?.destroy()
            false
        }
    }

    fun dismiss() {
        val view = overlayView ?: return
        unregisterConfigurationCallback()
        overlayView = null
        hostOwner?.destroy()
        hostOwner = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Focus overlay already detached", error)
        }
        view.disposeComposition()
        Log.d(TAG, "Focus overlay dismissed")
    }

    private fun currentDialogSize(): OverlayDialogSize {
        val density = context.resources.displayMetrics.density
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            (metrics.bounds.width() - insets.left - insets.right) to
                (metrics.bounds.height() - insets.top - insets.bottom)
        } else {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
        return OverlayLayoutPolicy.focusDialogSize(width / density, height / density)
    }

    private fun updateWindowSize(view: ComposeView) {
        val size = currentDialogSize()
        maxContentHeight = size.maxHeightDp.dp
        val params = view.layoutParams as WindowManager.LayoutParams
        params.width = (size.widthDp * context.resources.displayMetrics.density).roundToInt()
        windowManager.updateViewLayout(view, params)
        Log.d(TAG, "Focus overlay resized widthDp=${size.widthDp} maxHeightDp=${size.maxHeightDp}")
    }

    private fun unregisterConfigurationCallback() {
        if (!configurationRegistered) return
        context.unregisterComponentCallbacks(configurationCallbacks)
        configurationRegistered = false
    }

    private companion object {
        const val TAG = "FocusEnforcement"
    }
}
