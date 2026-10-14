package com.example.timemanager.ui.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutPolicyTest {
    @Test
    fun portraitAndNarrowSplitScreenKeepVerticalLayout() {
        assertFalse(OverlayLayoutPolicy.isWide(400f, 800f))
        assertFalse(OverlayLayoutPolicy.isWide(560f, 320f))
        assertFalse(OverlayLayoutPolicy.isWide(720f, 720f))
    }

    @Test
    fun shortWideViewportUsesTwoColumns() {
        assertTrue(OverlayLayoutPolicy.isWide(720f, 360f))
        assertTrue(OverlayLayoutPolicy.isWide(600f, 320f))
        assertTrue(OverlayLayoutPolicy.isWide(960f, 600f))
    }

    @Test
    fun focusDialogStaysSmallInPortraitAndFitsShortWideViewport() {
        val portrait = OverlayLayoutPolicy.focusDialogSize(432f, 900f)
        assertEquals(384f, portrait.widthDp, 0f)
        assertEquals(852f, portrait.maxHeightDp, 0f)

        val wide = OverlayLayoutPolicy.focusDialogSize(900f, 400f)
        assertEquals(720f, wide.widthDp, 0f)
        assertEquals(352f, wide.maxHeightDp, 0f)
        assertTrue(OverlayLayoutPolicy.isWide(wide.widthDp, wide.maxHeightDp))
    }

    @Test
    fun narrowDialogCannotExceedAvailableWidth() {
        val dialog = OverlayLayoutPolicy.focusDialogSize(360f, 640f)
        assertEquals(312f, dialog.widthDp, 0f)
        assertFalse(OverlayLayoutPolicy.isWide(dialog.widthDp, dialog.maxHeightDp))
    }

    @Test
    fun rotatingBackRestoresOriginalDialogDimensions() {
        val initial = OverlayLayoutPolicy.focusDialogSize(432f, 900f)
        val rotated = OverlayLayoutPolicy.focusDialogSize(900f, 432f)
        val restored = OverlayLayoutPolicy.focusDialogSize(432f, 900f)
        assertTrue(rotated.widthDp > initial.widthDp)
        assertEquals(initial, restored)
    }
}
