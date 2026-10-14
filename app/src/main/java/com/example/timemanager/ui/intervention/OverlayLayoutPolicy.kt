package com.example.timemanager.ui.intervention

/** Pure sizing rules shared by the Compose layout and the floating focus window. */
internal object OverlayLayoutPolicy {
    const val MIN_WIDE_WIDTH_DP = 600f
    const val WIDE_CARD_MAX_WIDTH_DP = 720f
    const val PORTRAIT_CARD_MAX_WIDTH_DP = 560f
    const val FOCUS_PORTRAIT_MAX_WIDTH_DP = 400f
    const val VIEWPORT_MARGIN_DP = 24f
    const val ACTIONS_WIDTH_DP = 220f

    fun isWide(widthDp: Float, heightDp: Float): Boolean =
        widthDp >= MIN_WIDE_WIDTH_DP && widthDp > heightDp

    fun focusDialogSize(viewportWidthDp: Float, viewportHeightDp: Float): OverlayDialogSize {
        val availableWidth = (viewportWidthDp - VIEWPORT_MARGIN_DP * 2).coerceAtLeast(1f)
        val availableHeight = (viewportHeightDp - VIEWPORT_MARGIN_DP * 2).coerceAtLeast(1f)
        val maxWidth = if (isWide(availableWidth, availableHeight)) {
            WIDE_CARD_MAX_WIDTH_DP
        } else {
            FOCUS_PORTRAIT_MAX_WIDTH_DP
        }
        return OverlayDialogSize(minOf(availableWidth, maxWidth), availableHeight)
    }
}

internal data class OverlayDialogSize(val widthDp: Float, val maxHeightDp: Float)
