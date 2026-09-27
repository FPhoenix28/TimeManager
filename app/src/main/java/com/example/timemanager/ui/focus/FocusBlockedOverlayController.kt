package com.example.timemanager.ui.focus

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.example.timemanager.ui.intervention.OverlayComposeHostOwner
import com.example.timemanager.ui.theme.TimeManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Accessibility-only overlay, separate from the daily-limit intervention controller. */
class FocusBlockedOverlayController(context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val context = context.applicationContext
    private var overlayView: ComposeView? = null
    private var hostOwner: OverlayComposeHostOwner? = null

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
            owner = OverlayComposeHostOwner().also { it.resume() }
            view = ComposeView(context).apply {
                owner.installViewTreeOwners(this)
                setContent {
                    TimeManagerTheme {
                        FocusBlockedContent(appName, endTimeMillis, onReturn)
                    }
                }
            }
            val density = context.resources.displayMetrics.density
            val dialogWidth = minOf(
                (400 * density).toInt(),
                context.resources.displayMetrics.widthPixels - (48 * density).toInt(),
            )
            val params = WindowManager.LayoutParams(
                dialogWidth,
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
            Log.d(TAG, "Focus overlay attached")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Focus overlay failed", error)
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

    private companion object {
        const val TAG = "FocusEnforcement"
    }
}
