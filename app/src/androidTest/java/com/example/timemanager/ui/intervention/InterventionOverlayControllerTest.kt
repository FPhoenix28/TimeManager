package com.example.timemanager.ui.intervention

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timemanager.data.model.LimitInterventionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterventionOverlayControllerTest {
    @Test
    fun overlayAttachesAndDisposesWithIndependentComposeHost() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue(Settings.canDrawOverlays(context))
        val controller = InterventionOverlayController(context)

        try {
            val shown = controller.show(
                request = LimitInterventionRequest(
                    packageName = "com.example.test",
                    appName = "Overlay test",
                    todayUsageSeconds = 3_900L,
                    baseLimitSeconds = 3_600L,
                    extensionSeconds = 300L,
                    effectiveLimitSeconds = 3_900L,
                    exceededSeconds = 300L,
                    currentSessionSeconds = 900L,
                    extensionsRemaining = 2,
                ),
                onExit = {},
                onContinue = {},
            )
            assertTrue(shown)
            delay(1_000L)
        } finally {
            controller.dismiss()
        }
    }
}
