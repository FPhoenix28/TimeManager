package com.example.timemanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnAppWindowIdentityTest {
    private val release = "com.fphoenix28.timemanager"
    private val dev = "$release.debug"
    private val mainActivity = "com.example.timemanager.MainActivity"

    @Test fun retainedMainActivityIsNotAnOverlayInEitherVariant() {
        for (own in listOf(release, dev)) {
            assertFalse(isOwnOverlayWindow(own, mainActivity, own))
        }
    }

    @Test fun ownComposeWindowIsTransientRegardlessOfApplicationId() {
        for (own in listOf(release, dev)) {
            assertTrue(isOwnOverlayWindow(own, "androidx.compose.ui.platform.ComposeView", own))
        }
    }

    @Test fun otherVariantAndFormerIdentityAreNotOurWindows() {
        assertFalse(isOwnOverlayWindow(release, "Overlay", dev))
        assertFalse(isOwnOverlayWindow(dev, "Overlay", release))
        assertFalse(isOwnOverlayWindow("com.example.timemanager", "Overlay", release))
    }
}
