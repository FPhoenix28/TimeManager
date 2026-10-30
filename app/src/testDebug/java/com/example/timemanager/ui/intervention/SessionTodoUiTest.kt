package com.example.timemanager.ui.intervention

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGate
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.intervention.InterventionPresentation
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.domain.todo.TodoSummaryItem
import com.example.timemanager.ui.theme.ThemeMode
import com.example.timemanager.ui.theme.TimeManagerTheme
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

/** In-process UI only: no service, system overlay, user data or device operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionTodoUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun summary(count: Int, longTitles: Boolean = false) = TodoSummary(
        totalCount = count,
        topItems = List(count) { index ->
            TodoSummaryItem(index.toLong(), if (longTitles) "待办 ${index + 1}：" + "读完这一章并整理笔记".repeat(8) else "待办 ${index + 1}")
        },
    )

    private fun request(
        phase: SessionGatePhase = SessionGatePhase.REMINDER,
        presentation: InterventionPresentation = InterventionPresentation.HOME_FIRST,
        waitMillis: Long = 0L,
    ) = LimitInterventionRequest.sessionReminder(
        packageName = "test.managed", appName = "测试应用", todayUsageSeconds = 1_800L,
        currentSessionSeconds = 600L, configuredReminderSeconds = 600L,
        gate = SessionGate(1L, phase, SystemClock.elapsedRealtime() + waitMillis),
        presentation = presentation,
    )

    private fun show(
        todos: TodoSummary = TodoSummary(),
        intervention: LimitInterventionRequest = request(),
        dark: Boolean = false,
        width: Int = 360,
        height: Int = 800,
        fontScale: Float = 1f,
        onHome: () -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        compose.setContent {
            TimeManagerTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface { Box(Modifier.size(width.dp, height.dp)) {
                        LimitReachedScreen(intervention, onHome, onContinue, todos)
                    } }
                }
            }
        }
    }

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/reports/todo-session-phase2").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertTwoActions(homeLabel: String = "返回桌面", enabled: Boolean = true) {
        val home = compose.onNodeWithText(homeLabel).assertIsDisplayed()
        val continueApp = compose.onNodeWithText("继续使用").assertIsDisplayed()
        if (enabled) { home.assertIsEnabled(); continueApp.assertIsEnabled() }
        else { home.assertIsNotEnabled(); continueApp.assertIsNotEnabled() }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithText("5 分钟后静心").assertDoesNotExist()
        compose.onNodeWithText("延长", substring = true).assertDoesNotExist()
    }

    @Test fun noTodosKeepsOriginalReminderWithoutCardOrNewAction() {
        show()
        compose.onNodeWithText("先停一下").assertIsDisplayed()
        compose.onNodeWithText("你已经连续使用测试应用10分钟。").assertIsDisplayed()
        compose.onNodeWithText("不如先完成一件小事？").assertDoesNotExist()
        compose.onNodeWithText("今天还有", substring = true).assertDoesNotExist()
        assertTwoActions()
        capture("empty-light")
    }

    @Test fun oneTodoShowsInvitationAndTitle() {
        show(summary(1))
        compose.onNodeWithText("今天还有 1 项待办").assertIsDisplayed()
        compose.onNodeWithText("不如先完成一件小事？").assertIsDisplayed()
        compose.onNodeWithText("待办 1").assertIsDisplayed()
        compose.onNodeWithText("还有 1 项").assertDoesNotExist()
        assertTwoActions()
        capture("one-light")
    }

    @Test fun threeTodosKeepTheirOrderAndActionsVisible() {
        show(summary(3))
        val bounds = (1..3).map { compose.onNodeWithText("待办 $it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.zipWithNext().all { (first, next) -> first.bottom < next.top })
        assertTwoActions()
        capture("three-light")
    }

    @Test fun fiveTodosShowThreeAndTwoMoreInDarkTheme() {
        show(summary(5), dark = true)
        compose.onNodeWithText("今天还有 5 项待办").assertIsDisplayed()
        (1..3).forEach { compose.onNodeWithText("待办 $it").assertIsDisplayed() }
        compose.onNodeWithText("待办 4").assertDoesNotExist()
        compose.onNodeWithText("待办 5").assertDoesNotExist()
        compose.onNodeWithText("还有 2 项").assertIsDisplayed()
        assertTwoActions()
        capture("five-dark")
    }

    @Test fun countdownDisablesBothActionsAndThenEnablesSameCallbacks() {
        compose.mainClock.autoAdvance = false
        var homes = 0
        var continues = 0
        show(summary(3), intervention = request(waitMillis = 5_000L),
            onHome = { homes++ }, onContinue = { continues++ })
        compose.mainClock.advanceTimeByFrame()
        assertTwoActions(enabled = false)
        compose.onNodeWithText("返回桌面").performClick()
        compose.onNodeWithText("继续使用").performClick()
        compose.runOnIdle { assertEquals(0, homes); assertEquals(0, continues) }
        capture("countdown-three-light")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        compose.mainClock.advanceTimeBy(1_000L)
        assertTwoActions(enabled = false)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText("可以决定了").assertIsDisplayed()
        assertTwoActions()
        compose.onNodeWithText("继续使用").performClick()
        compose.runOnIdle { assertEquals(1, continues); assertEquals(0, homes) }
        compose.onNodeWithText("返回桌面").performClick()
        compose.runOnIdle { assertEquals(1, homes) }
    }

    @Test fun reentryPreservesCopyWaitAndStayHomeCallback() {
        compose.mainClock.autoAdvance = false
        var homes = 0
        show(summary(1), intervention = request(phase = SessionGatePhase.REENTRY, waitMillis = 5_000L),
            onHome = { homes++ })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("你刚刚已经连续使用过测试应用。").assertIsDisplayed()
        compose.onNodeWithText("如果仍然想继续，请等待片刻再决定。").assertIsDisplayed()
        compose.onNodeWithText("返回桌面").assertDoesNotExist()
        assertTwoActions(homeLabel = "留在桌面", enabled = false)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText("稍作停留").assertIsDisplayed()
        assertTwoActions(homeLabel = "留在桌面")
        compose.onNodeWithText("留在桌面").performClick()
        compose.runOnIdle { assertEquals(1, homes) }
        capture("reentry-light")
    }

    @Test fun fallbackRetainsWaitThenContinueAndHomeChoices() {
        compose.mainClock.autoAdvance = false
        var continues = 0
        show(summary(3), intervention = request(presentation = InterventionPresentation.OVERLAY_IN_PLACE, waitMillis = 5_000L),
            dark = true, onContinue = { continues++ })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("自动返回桌面暂不可用，可在倒计时结束后选择返回桌面。").assertIsDisplayed()
        assertTwoActions(enabled = false)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        compose.mainClock.advanceTimeBy(1_000L)
        assertTwoActions()
        compose.onNodeWithText("继续使用").performClick()
        compose.runOnIdle { assertEquals(1, continues) }
        capture("fallback-dark")
    }

    @Test fun summaryRefreshDoesNotReplaceGateOrUnlockActions() {
        val intervention = request(waitMillis = 60_000L)
        var todos by mutableStateOf(summary(1))
        compose.setContent { TimeManagerTheme { LimitReachedScreen(intervention, {}, {}, todos) } }
        compose.onNodeWithText("今天还有 1 项待办").assertIsDisplayed()
        compose.runOnIdle { todos = summary(5) }
        compose.onNodeWithText("今天还有 5 项待办").assertIsDisplayed()
        assertTwoActions(enabled = false)
        compose.runOnIdle { todos = TodoSummary() }
        compose.onNodeWithText("不如先完成一件小事？").assertDoesNotExist()
        assertTwoActions(enabled = false)
    }

    @Test fun narrowLargeFontKeepsCountdownAndActionsVisibleWithScrollableTodos() {
        val todos = summary(5, longTitles = true)
        show(todos, intervention = request(waitMillis = 60_000L), dark = true,
            width = 320, height = 480, fontScale = 2f)
        compose.onNodeWithText("先停一下").assertIsDisplayed()
        compose.onNodeWithText("60").assertIsDisplayed()
        assertTwoActions(enabled = false)
        capture("small-large-font-dark")
        todos.topItems.take(3).forEach { todo ->
            compose.onNodeWithText(todo.title).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(todo.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertTrue(layouts.single().isLineEllipsized(0))
            assertTwoActions(enabled = false)
        }
        compose.onNodeWithText("还有 2 项").performScrollTo().assertIsDisplayed()
        capture("small-large-font-todos-dark")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeKeepsIndependentActionColumnAndTruncatesLongTitles() {
        val todos = summary(5, longTitles = true)
        show(todos, width = 800, height = 360)
        compose.onNodeWithText("先停一下").assertIsDisplayed()
        assertTwoActions()
        compose.onNodeWithText(todos.topItems[2].title).performScrollTo().assertIsDisplayed()
        assertTwoActions()
        capture("landscape-light")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeLargeFontKeepsCountdownAndButtonsVisible() {
        show(summary(5, longTitles = true), intervention = request(waitMillis = 60_000L),
            dark = true, width = 800, height = 360, fontScale = 2f)
        compose.onNodeWithText("先停一下").assertIsDisplayed()
        compose.onNodeWithText("60").assertIsDisplayed()
        assertTwoActions(enabled = false)
        capture("landscape-large-font-dark")
        compose.onNodeWithText("还有 2 项").performScrollTo().assertIsDisplayed()
        assertTwoActions(enabled = false)
    }

    @Test fun portraitLongTitleUsesTwoLineEllipsis() {
        val todos = summary(1, longTitles = true)
        show(todos)
        compose.onNodeWithText(todos.topItems.single().title).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(todos.topItems.single().title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(1))
        assertTwoActions()
    }

    @Test fun dailyStillUsesOriginalSummaryAndExtensionActions() {
        val daily = LimitInterventionRequest(
            packageName = "test.managed", appName = "测试应用", todayUsageSeconds = 3_600L,
            baseLimitSeconds = 3_600L, extensionSeconds = 0L, effectiveLimitSeconds = 3_600L,
            exceededSeconds = 0L, currentSessionSeconds = 600L, extensionDurationSeconds = 900L,
        )
        show(summary(3), intervention = daily)
        compose.onNodeWithText("今日额度已用完").assertIsDisplayed()
        compose.onNodeWithText("不如先完成一件小事？").assertDoesNotExist()
        compose.onNodeWithText("○ 待办 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("○ 待办 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("延长 15 分钟").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("退出应用").performScrollTo().assertIsEnabled()
        compose.mainClock.advanceTimeBy(5_100L)
        compose.onNodeWithText("延长 15 分钟").assertIsEnabled()
        capture("daily-unchanged-light")
    }
}
