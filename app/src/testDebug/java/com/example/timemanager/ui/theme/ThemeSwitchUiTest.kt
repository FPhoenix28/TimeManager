package com.example.timemanager.ui.theme

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGate
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.data.preferences.AppearancePreferences
import com.example.timemanager.domain.todo.TodoRuntime
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.ui.focus.DeepFocusContent
import com.example.timemanager.ui.focus.FocusBlockedContent
import com.example.timemanager.ui.home.AppUsageWithLimit
import com.example.timemanager.ui.home.HomeDashboard
import com.example.timemanager.ui.intervention.LimitReachedScreen
import com.example.timemanager.ui.intervention.OverlayComposeHostOwner
import com.example.timemanager.ui.permissions.PermissionSettingsScreen
import com.example.timemanager.ui.todo.TodoViewModel
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses an Activity window with independent overlay owners. No system overlay is installed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeSwitchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val preferences by lazy { AppearancePreferences(compose.activity.applicationContext) }
    @Volatile private var palette: Pair<Color, Color>? = null

    @Composable
    private fun PaletteProbe() {
        val current = MaterialTheme.colorScheme.primary to TimeManagerThemeValues.semanticColors.warning
        SideEffect { palette = current }
    }

    private fun expectPalette(dark: Boolean) {
        val expected = if (dark) PrimaryDark to WarningDark else PrimaryLight to WarningLight
        compose.waitUntil(5_000L) { palette == expected }
    }

    private fun setMode(mode: ThemeMode) = runBlocking { preferences.setThemeMode(mode) }

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/reports/icon-system-v1").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun featureArtworkDecodesAtOriginalSizeWithTransparentCorners() {
        val resources = compose.activity.resources
        listOf(R.drawable.ic_tm_focus, R.drawable.ic_tm_deep_focus, R.drawable.ic_tm_todo,
            R.drawable.ic_tm_usage, R.drawable.ic_tm_session).forEach { resource ->
            val bitmap = checkNotNull(BitmapFactory.decodeResource(resources, resource))
            try {
                assertEquals(1024, bitmap.width)
                assertEquals(1024, bitmap.height)
                assertTrue(bitmap.hasAlpha())
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun settingsSwitchImmediatelyAndSystemResumesFollowingDeviceAppearance() {
        setMode(ThemeMode.SYSTEM)
        compose.setContent {
            StoredTimeManagerTheme { mode ->
                PaletteProbe()
                val scope = rememberCoroutineScope()
                Surface {
                    PermissionSettingsScreen(
                        state = PermissionHealthState(true, true, true, true, true),
                        themeMode = mode,
                        onThemeModeSelected = { selection -> scope.launch { preferences.setThemeMode(selection) } },
                        onBack = {},
                        onRequestUsageAccess = {},
                        onRequestNotification = {},
                        onRequestOverlay = {},
                        onRequestAccessibility = {},
                    )
                }
            }
        }
        dispatchNightMode(dark = false)
        expectPalette(dark = false)
        compose.onNodeWithText("跟随系统").assertIsSelected()
        dispatchNightMode(dark = true)
        expectPalette(dark = true)
        compose.onNodeWithText("浅色模式").performClick()
        expectPalette(dark = false)
        compose.onNodeWithText("浅色模式").assertIsSelected()
        capture("settings-light")
        dispatchNightMode(dark = false)
        compose.onNodeWithText("深色模式").performClick()
        expectPalette(dark = true)
        compose.onNodeWithText("深色模式").assertIsSelected()
        capture("settings-dark")
        compose.onNodeWithText("跟随系统").performClick()
        expectPalette(dark = false)
        compose.onNodeWithText("跟随系统").assertIsSelected()
    }

    private fun dispatchNightMode(dark: Boolean) {
        compose.runOnUiThread {
            val configuration = Configuration(compose.activity.resources.configuration)
            val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            compose.activity.window.decorView.dispatchConfigurationChanged(configuration)
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun settingsPermissionsAndBackStayReachableWithLargeTextInLandscape() {
        setMode(ThemeMode.SYSTEM)
        val actions = mutableListOf<String>()
        compose.setContent {
            StoredTimeManagerTheme { mode ->
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Surface {
                        Box(Modifier.size(480.dp, 320.dp)) {
                            PermissionSettingsScreen(
                                state = PermissionHealthState(false, false, false),
                                themeMode = mode,
                                onThemeModeSelected = { actions += it.name },
                                onBack = { actions += "back" },
                                onRequestUsageAccess = { actions += "usage" },
                                onRequestNotification = { actions += "notification" },
                                onRequestOverlay = { actions += "overlay" },
                                onRequestAccessibility = { actions += "accessibility" },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("深色模式").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("使用情况访问"))
        compose.onAllNodesWithText("去开启")[0].performScrollTo().performClick()
        compose.onNodeWithText("允许通知").performScrollTo().performClick()
        compose.onAllNodesWithText("去开启")[1].performScrollTo().performClick()
        compose.onNodeWithText("前往开启").performScrollTo().performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(listOf("DARK", "usage", "notification", "overlay", "accessibility", "back"), actions)
        }
    }

    @Test fun homeRecolorsInPlace() {
        val todoViewModel = TodoViewModel(TodoRuntime.get(compose.activity).repository)
        setMode(ThemeMode.LIGHT)
        compose.setContent {
            StoredTimeManagerTheme {
                PaletteProbe()
                Surface {
                    HomeDashboard(
                        todoViewModel = todoViewModel,
                        onOpenTodos = {},
                        appUsages = listOf(AppUsageWithLimit("test.app", "受管理应用", null, 2_400, 3_600, 0, 3_600)),
                        permissionHealth = PermissionHealthState(true, true, true, true, true),
                        onOpenPermissionSettings = {}, onOpenManagement = {}, onOpenAppDetail = {},
                        onOpenStatistics = {}, focusState = FocusModeState(), onOpenFocus = {}, onOpenDeepFocus = {},
                    )
                }
            }
        }
        expectPalette(dark = false)
        compose.onNodeWithText("今日已使用").assertIsDisplayed()
        capture("home-light")
        setMode(ThemeMode.DARK)
        expectPalette(dark = true)
        compose.onNodeWithText("今日待办").assertIsDisplayed()
        capture("home-dark")
        setMode(ThemeMode.SYSTEM)
        dispatchNightMode(dark = false)
        expectPalette(dark = false)
        dispatchNightMode(dark = true)
        expectPalette(dark = true)
        capture("home-system-dark")
    }

    @Test fun sessionGateKeepsWaitingWhenTheOverlayRecolors() {
        val request = sessionRequest(waitMillis = 60_000L)
        withOverlay(content = {
            LimitReachedScreen(request, {}, {}, onScheduleDeepFocus = {})
        }) {
            compose.onNodeWithText("60").assertIsDisplayed()
            compose.onNodeWithText("5 分钟后静心").assertIsNotEnabled()
            capture("session-light")
            setMode(ThemeMode.DARK)
            expectPalette(dark = true)
            compose.onNodeWithText("60").assertIsDisplayed()
            compose.onNodeWithText("5 分钟后静心").assertIsNotEnabled()
            capture("session-dark")
            setMode(ThemeMode.SYSTEM)
            dispatchNightMode(dark = false)
            expectPalette(dark = false)
            dispatchNightMode(dark = true)
            expectPalette(dark = true)
            compose.onNodeWithText("60").assertIsDisplayed()
            compose.onNodeWithText("5 分钟后静心").assertIsNotEnabled()
        }
    }

    @Test fun confirmationStaysOpenAcrossThemeChangeAndSchedulesOnlyOnClick() {
        val request = sessionRequest(waitMillis = 0L)
        var scheduled = 0
        withOverlay(content = {
            LimitReachedScreen(request, {}, {}, onScheduleDeepFocus = { scheduled++ })
        }) {
            compose.onNodeWithText("5 分钟后静心").performClick()
            compose.onNodeWithText("准备开始静心").assertIsDisplayed()
            capture("confirmation-light")
            setMode(ThemeMode.DARK)
            expectPalette(dark = true)
            compose.onNodeWithText("准备开始静心").assertIsDisplayed()
            compose.onNodeWithText("取消").assertIsDisplayed()
            capture("confirmation-dark")
            compose.runOnIdle { assertEquals(0, scheduled) }
            compose.onNodeWithText("确认安排").performClick()
            compose.runOnIdle { assertEquals(1, scheduled) }
        }
    }

    @Test fun dailyAndFocusBlockUseTheSelectedPalette() {
        val showDaily = mutableStateOf(true)
        val end = System.currentTimeMillis() + 60_000L
        val request = LimitInterventionRequest(
            packageName = "test.app", appName = "测试应用", todayUsageSeconds = 3_600,
            baseLimitSeconds = 3_600, extensionSeconds = 0, effectiveLimitSeconds = 3_600,
            exceededSeconds = 0, currentSessionSeconds = 20, extensionDurationSeconds = 900,
        )
        withOverlay(content = {
            if (showDaily.value) LimitReachedScreen(request, {}, {})
            else FocusBlockedContent("测试应用", end, {})
        }) {
            compose.onNodeWithText("今日额度已用完").assertIsDisplayed()
            setMode(ThemeMode.DARK)
            expectPalette(dark = true)
            compose.onNodeWithText("今日额度已用完").assertIsDisplayed()
            compose.runOnIdle { showDaily.value = false }
            compose.onNodeWithText("专注进行中").assertIsDisplayed()
            setMode(ThemeMode.LIGHT)
            expectPalette(dark = false)
            compose.onNodeWithText("专注进行中").assertIsDisplayed()
        }
    }

    @Test fun deepFocusOverlayRecolorsWithoutRestartingContent() {
        val now = System.currentTimeMillis()
        val focus = FocusModeState(FocusModeType.DEEP_FOCUS, startTimeMillis = now,
            endTimeMillis = now + 30 * 60_000L)
        withOverlay(content = { DeepFocusContent(focus, true, true, {}, {}) }) {
            compose.onNodeWithText("静心模式").assertIsDisplayed()
            capture("deep-focus-light")
            setMode(ThemeMode.DARK)
            expectPalette(dark = true)
            compose.onNodeWithText("长按 3 秒\n提前结束").assertIsDisplayed()
            capture("deep-focus-dark")
        }
    }

    private fun sessionRequest(waitMillis: Long) = LimitInterventionRequest.sessionReminder(
        "test.app", "测试应用", 1_800, 600, 600,
        SessionGate(1L, SessionGatePhase.REMINDER, SystemClock.elapsedRealtime() + waitMillis),
    )

    private fun withOverlay(content: @Composable () -> Unit, check: () -> Unit) {
        setMode(ThemeMode.LIGHT)
        lateinit var owner: OverlayComposeHostOwner
        lateinit var view: ComposeView
        lateinit var container: FrameLayout
        var activeCompositions = 0
        var totalCompositions = 0
        compose.runOnUiThread {
            owner = OverlayComposeHostOwner().also { it.resume() }
            view = ComposeView(compose.activity.applicationContext).also { owner.installViewTreeOwners(it) }
            view.setContent {
                StoredTimeManagerTheme {
                    PaletteProbe()
                    DisposableEffect(Unit) {
                        activeCompositions++
                        totalCompositions++
                        onDispose { activeCompositions-- }
                    }
                    Surface(Modifier.fillMaxSize(), content = content)
                }
            }
            container = FrameLayout(compose.activity)
            compose.activity.setContentView(container)
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
        try {
            expectPalette(dark = false)
            check()
            compose.runOnIdle {
                assertSame(view, container.getChildAt(0))
                assertEquals(1, totalCompositions)
                assertEquals(1, activeCompositions)
            }
        } finally {
            compose.runOnUiThread { owner.destroy(); container.removeView(view); view.disposeComposition() }
        }
        compose.runOnIdle { assertEquals(0, activeCompositions) }
    }
}
