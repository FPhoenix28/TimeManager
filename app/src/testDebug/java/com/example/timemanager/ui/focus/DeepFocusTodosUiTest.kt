package com.example.timemanager.ui.focus

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.timemanager.data.local.TodoDao
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.data.local.TodoReminderReceiptEntity
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.domain.todo.TodoDayState
import com.example.timemanager.ui.intervention.OverlayComposeHostOwner
import com.example.timemanager.ui.theme.ThemeMode
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.utils.UsageDayProvider
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host UI and independent ComposeView tests; never installs an APK or creates a system overlay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepFocusTodosUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val day = "2026-10-30"
    private val holdText = "长按 3 秒\n提前结束"

    @After fun tearDown() { scope.cancel() }

    private fun todos(count: Int, longTitle: Boolean = false) = List(count) { index ->
        TodoEntity(id = index + 1L, title = if (longTitle) "待办 ${index + 1}：" + "读完这一章并整理笔记".repeat(12) else "待办 ${index + 1}",
            createdAtMillis = index.toLong(), createdUsageDay = day)
    }
    private fun state(count: Int, longTitle: Boolean = false) =
        MutableStateFlow(TodoDayState.from(todos(count, longTitle), emptyList(), day))

    private fun focus() = System.currentTimeMillis().let { now -> FocusModeState(
        mode = FocusModeType.DEEP_FOCUS, startTimeMillis = now - 60_000L, endTimeMillis = now + 29 * 60_000L,
    ) }

    private fun show(
        tasks: StateFlow<TodoDayState> = state(0), dark: Boolean = false,
        width: Int = 360, height: Int = 800, fontScale: Float = 1f,
        active: FocusModeState = focus(), accessibility: Boolean = true, overlay: Boolean = true,
        onEnd: () -> Unit = {}, onCompleted: () -> Unit = {},
        onAccessibility: () -> Unit = {}, onOverlay: () -> Unit = {},
    ) {
        compose.setContent {
            TimeManagerTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        DeepFocusTodoContent(active, tasks, accessibility, overlay, onEnd, onCompleted,
                            onAccessibility, onOverlay)
                    }
                }
            }
        }
    }

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/reports/ui-polish/deep-focus").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun emptyStateKeepsOriginalGuidanceAndEndControl() {
        show()
        compose.onNodeWithText("现在不需要做任何事").assertIsDisplayed()
        compose.onNodeWithText("今日待办").assertDoesNotExist()
        compose.onNodeWithText("暂时放下手机，专注眼前的事情").assertDoesNotExist()
        compose.onNodeWithText(holdText).assertIsDisplayed()
        capture("deep-empty-light")
    }

    @Test fun oneTodoHasNoExtraControlsOrUnnecessaryListSpace() {
        show(state(1))
        compose.onNodeWithText("1 项未完成").assertIsDisplayed()
        compose.onNodeWithText("待办 1").assertIsDisplayed()
        compose.onNodeWithText("滑动查看全部待办").assertDoesNotExist()
        compose.onNodeWithText("现在不需要做任何事").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText("待办 1").fetchSemanticsNode().boundsInRoot
        assertTrue(list.height < row.height * 2)
    }

    @Test fun threeTodosPreserveOrderAndLightLayout() {
        show(state(3))
        compose.onNodeWithText("今日待办").assertIsDisplayed()
        compose.onNodeWithText("3 项未完成").assertIsDisplayed()
        val bounds = (1..3).map { compose.onNodeWithText("待办 $it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.zipWithNext().all { (first, next) -> first.bottom <= next.top })
        compose.onNodeWithText(holdText).assertIsDisplayed()
        capture("deep-three-light")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
    fun tenTodosAreAllReachableWithoutMovingTheExitControl() {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "24")
        show(state(10), dark = true)
        compose.onNodeWithText("10 项未完成").assertIsDisplayed()
        compose.onNodeWithText("滑动查看全部待办").assertIsDisplayed()
        val before = compose.onNodeWithText(holdText).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        capture("deep-ten-dark")
        val list = compose.onNode(hasScrollToIndexAction())
        (0..9).forEach { index ->
            list.performScrollToIndex(index)
            compose.onNodeWithText("待办 ${index + 1}").assertIsDisplayed()
            assertEquals(before, compose.onNodeWithText(holdText).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
    fun endTimeUsesChineseLocaleAndDeviceClockPreference() {
        val now = ZonedDateTime.now()
        val afternoon = now.toLocalDate().plusDays(1).atTime(15, 0).atZone(now.zone)
        val settings = compose.activity.contentResolver
        Settings.System.putString(settings, Settings.System.TIME_12_24, "24")
        val state = focus().copy(endTimeMillis = afternoon.toInstant().toEpochMilli())
        show(active = state)
        compose.onNodeWithText("结束时间  15:00").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
    fun endTimeCanUseTwelveHourClock() {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "12")
        show()
        compose.onNode(hasText("结束时间", substring = true) and
            (hasText("AM", substring = true) or hasText("PM", substring = true))).assertIsDisplayed()
    }

    @Test fun repositoryCompletionAndFourAmCarryOverRefreshTheSameList() {
        val provider = UsageDayProvider(Clock.fixed(
            OffsetDateTime.parse("2026-10-30T03:59:59+08:00").toInstant(), ZoneId.of("Asia/Shanghai")))
        val days = MutableStateFlow(provider.currentUsageDay())
        val dao = DeepTodosDao()
        val repository = TodoRepository(dao, days, scope, { days.value.startTimeMillis + 1_000 }, { provider })
        val finishedId = runBlocking { repository.create("已经完成") }
        runBlocking { repository.create("保留的任务"); repository.complete(finishedId) }
        show(repository.dayState)
        compose.onNodeWithText("已经完成").assertDoesNotExist()
        compose.onNodeWithText("保留的任务").assertIsDisplayed()
        compose.runOnIdle { days.value = provider.usageDay(java.time.LocalDate.parse(day)) }
        compose.onNodeWithText("保留的任务").assertIsDisplayed()
        compose.runOnIdle { assertEquals("保留的任务", repository.dayState.value.carried.single().title) }
        val nextId = runBlocking { dao.insertTodo(TodoEntity(title = "今天新任务", createdAtMillis = provider.currentUsageDay().endTimeMillis + 1, createdUsageDay = day)) }
        compose.onNodeWithText("2 项未完成").assertIsDisplayed()
        runBlocking { repository.complete(nextId) }
        compose.onNodeWithText("1 项未完成").assertIsDisplayed()
        compose.onNodeWithText("今天新任务").assertDoesNotExist()
    }

    @Test fun clearingTasksSwitchesGuidanceAndHidesCard() {
        val tasks = state(3)
        show(tasks)
        compose.runOnIdle { tasks.value = TodoDayState(usageDay = day) }
        compose.onNodeWithText("今日待办").assertDoesNotExist()
        compose.onNodeWithText("现在不需要做任何事").assertIsDisplayed()
        compose.onNodeWithText(holdText).assertIsDisplayed()
    }

    @Test fun longTitlesSmallScreenAndLargeFontRemainScrollable() {
        val tasks = state(10, longTitle = true)
        show(tasks, dark = true, width = 320, height = 480, fontScale = 2f)
        compose.onNodeWithText("静心模式").assertIsDisplayed()
        compose.onNodeWithText(holdText).assertIsDisplayed()
        val holdLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(holdText).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(holdLayouts) }
        assertEquals(2, holdLayouts.single().lineCount)
        assertFalse(holdLayouts.single().didOverflowHeight)
        capture("deep-small-large-font-dark")
        compose.onNodeWithText("今日待办").performScrollTo().assertIsDisplayed()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollTo()
        list.performScrollToIndex(9)
        val lastTitle = tasks.value.unfinished.last().title
        compose.onNodeWithText(lastTitle).assertExists()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(lastTitle).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().didOverflowWidth)
        compose.onNodeWithText("静心模式").assertIsDisplayed()
        compose.onNode(SemanticsMatcher("countdown") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.matches(Regex("\\d{2}:\\d{2}")) }
        }).assertIsDisplayed()
        compose.onNodeWithText(holdText).assertIsDisplayed()
        capture("deep-small-todos-dark")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeKeepsEndControlSeparateFromTaskScrolling() {
        show(state(10), dark = true, width = 800, height = 360)
        compose.onNodeWithText(holdText).assertIsDisplayed()
        compose.onNodeWithText("今日待办").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollTo().performScrollToIndex(9)
        compose.onNodeWithText("待办 10").assertIsDisplayed()
        compose.onNodeWithText(holdText).assertIsDisplayed()
        capture("deep-landscape-dark")
    }

    @Test fun countdownContinuesAcrossTodoUpdates() {
        compose.mainClock.autoAdvance = false
        val tasks = state(1)
        val active = focus()
        show(tasks, active = active)
        compose.mainClock.advanceTimeByFrame()
        fun displayedTime(): String = compose.onNode(SemanticsMatcher("timer") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.matches(Regex("\\d{2}:\\d{2}")) }
        }).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val before = displayedTime()
        // Production uses the real JVM wall clock; the Compose test clock only drives delays/frames.
        Thread.sleep(1_200L)
        compose.mainClock.advanceTimeBy(1_100L)
        val after = displayedTime()
        assertNotEquals(before, after)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { tasks.value = state(3).value }
        compose.onNodeWithText("3 项未完成").assertIsDisplayed()
        assertNotEquals(before, displayedTime())
        assertEquals(30 * 60_000L, active.endTimeMillis - active.startTimeMillis)
    }

    @Test fun taskScrollingDoesNotBreakThreeSecondHoldAndConfirmation() {
        var ended = 0
        show(state(10), onEnd = { ended++ })
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
        compose.mainClock.autoAdvance = false
        val hold = compose.onNodeWithText(holdText)
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_000L)
        hold.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("确定提前结束吗？").assertDoesNotExist()
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_900L)
        compose.onNodeWithText("确定提前结束吗？").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400L)
        hold.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("确定提前结束吗？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, ended) }
        compose.onNodeWithText("继续静心").performClick()
        compose.onNodeWithText("确定提前结束吗？").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(3_300L)
        hold.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("结束静心").performClick()
        compose.runOnIdle { assertEquals(1, ended) }
    }

    @Test fun completedPageDoesNotShowTodosOrChangeCompletionAction() {
        var completed = 0
        show(state(10), active = focus().copy(status = FocusModeStatus.COMPLETED), onCompleted = { completed++ })
        compose.onNodeWithText("静心结束").assertIsDisplayed()
        compose.onNodeWithText("这段时间结束了").assertIsDisplayed()
        compose.onNodeWithText("今日待办").assertDoesNotExist()
        compose.onNodeWithText(holdText).assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun permissionHintsAndRecoveryCallbacksRemainAvailable() {
        var accessibility = false
        var overlay = false
        show(state(10), accessibility = false, overlay = false,
            onAccessibility = { accessibility = true }, onOverlay = { overlay = true })
        compose.onNodeWithText("重新开启").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("恢复悬浮窗权限").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(accessibility); assertTrue(overlay) }
        compose.onNodeWithText(holdText).assertIsDisplayed()
    }

    @Test fun activityCompositionReleasesTodoSubscriptionWhenRemoved() {
        val tasks = state(1)
        var visible by mutableStateOf(true)
        val active = focus()
        compose.setContent { if (visible) TimeManagerTheme {
            DeepFocusTodoContent(active, tasks, true, true, {}, {})
        } }
        compose.onNodeWithText("待办 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, tasks.subscriptionCount.value); visible = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, tasks.subscriptionCount.value) }
    }

    @Test fun independentComposeHostUsesSameFullFlowAndDisposesSubscription() {
        val tasks = state(10)
        val active = focus()
        lateinit var owner: OverlayComposeHostOwner
        lateinit var view: ComposeView
        lateinit var container: FrameLayout
        compose.runOnUiThread {
            owner = OverlayComposeHostOwner().also { it.resume() }
            view = ComposeView(compose.activity).also { owner.installViewTreeOwners(it) }
            view.setContent { TimeManagerTheme {
                DeepFocusTodoContent(active, tasks, true, true, {}, {})
            } }
            container = FrameLayout(compose.activity)
            compose.activity.setContentView(container)
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
        try {
            compose.onNodeWithText("10 项未完成").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
            compose.onNodeWithText("待办 10").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, tasks.subscriptionCount.value); tasks.value = state(1).value }
            compose.onNodeWithText("1 项未完成").assertIsDisplayed()
            compose.onNodeWithText(holdText).assertIsDisplayed()
        } finally {
            compose.runOnUiThread { owner.destroy(); container.removeView(view); view.disposeComposition() }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, tasks.subscriptionCount.value) }
    }
}

private class DeepTodosDao : TodoDao {
    val rows = MutableStateFlow<List<TodoEntity>>(emptyList())
    override suspend fun insertTodo(todo: TodoEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value += todo.copy(id = id)
        return id
    }
    override suspend fun markCompleted(id: Long, completedAt: Long) { rows.value = rows.value.map { if (it.id == id) it.copy(completedAtMillis = completedAt) else it } }
    override suspend fun markUncompleted(id: Long) { rows.value = rows.value.map { if (it.id == id) it.copy(completedAtMillis = null) else it } }
    override suspend fun deleteTodo(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override fun observeUnfinishedTodos() = rows.map { it.filterNot(TodoEntity::isCompleted) }
    override fun observeCompletedTodosForCurrentDay(startMillis: Long, endMillis: Long) = rows.map { list -> list.filter { (it.completedAtMillis ?: -1) in startMillis until endMillis } }
    override fun observeCarriedTodos(day: String) = rows.map { list -> list.filter { !it.isCompleted && it.createdUsageDay < day } }
    override fun observeTodayTodos(day: String) = rows.map { list -> list.filter { !it.isCompleted && it.createdUsageDay == day } }
    override suspend fun getUnfinishedTodosSnapshot() = rows.value.filterNot(TodoEntity::isCompleted)
    override suspend fun claimReminder(receipt: TodoReminderReceiptEntity) = 1L
}
