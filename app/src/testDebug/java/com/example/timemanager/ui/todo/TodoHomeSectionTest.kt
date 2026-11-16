package com.example.timemanager.ui.todo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.timemanager.data.local.TodoDao
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.data.local.TodoReminderReceiptEntity
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.ui.home.AppUsageWithLimit
import com.example.timemanager.ui.home.FocusHomeFallback
import com.example.timemanager.ui.home.HomeDashboard
import com.example.timemanager.ui.theme.ThemeMode
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.utils.UsageDayProvider
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Debug host-only UI tests. The plain Application prevents production scheduling/services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodoHomeSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val provider = UsageDayProvider(Clock.fixed(
        OffsetDateTime.parse("2026-10-30T12:00:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"),
    ))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dao: HomeTodoDao
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    @Before fun setUp() {
        dao = HomeTodoDao()
        repository = TodoRepository(dao, flowOf(provider.currentUsageDay()), scope,
            { provider.currentUsageDay().startTimeMillis + 1_000L }, { provider })
        viewModel = TodoViewModel(repository)
    }

    @After fun tearDown() { scope.cancel() }

    private fun seed(count: Int) = runBlocking {
        repeat(count) { repository.create("待办 ${it + 1}") }
    }

    private fun showCard(dark: Boolean = false, width: Int = 328, fontScale: Float = 1f, viewportHeight: Int = 800,
                         onOpenAll: () -> Unit = {}) {
        compose.setContent {
            TimeManagerTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface {
                        Box(Modifier.width(width.dp).heightIn(max = viewportHeight.dp).verticalScroll(rememberScrollState())) {
                            TodoHomeSection(viewModel, onOpenAll)
                        }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        // Draw locally instead of PixelCopy, which waits for a device window frame.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/todo-session-final-polish/home").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun emptyCardKeepsQuickAddAndViewAll() {
        var opened = false
        showCard(onOpenAll = { opened = true })
        compose.onNodeWithText("0 项未完成").assertDoesNotExist()
        compose.onNodeWithText("写下一件想完成的事").assertIsDisplayed()
        compose.onNodeWithText("＋ 添加待办").assertIsDisplayed()
        capture("empty-light")
        compose.onNodeWithText("查看全部").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun oneTaskShowsTitleAndCheckbox() {
        seed(1)
        showCard()
        compose.onNodeWithText("1 项未完成").assertDoesNotExist()
        compose.onNodeWithText("待办 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("完成状态：待办 1").assertIsOff()
        compose.onAllNodes(hasText("还有", substring = true)).assertCountEquals(0)
        capture("one-light")
    }

    @Test fun threeTasksKeepRepositoryOrderWithoutOverflow() {
        seed(3)
        showCard()
        compose.onNodeWithText("3 项未完成").assertDoesNotExist()
        val bounds = (1..3).map { compose.onNodeWithText("待办 $it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.zipWithNext().all { (first, next) -> first.bottom <= next.top })
        compose.onAllNodes(hasText("还有", substring = true)).assertCountEquals(0)
        capture("three-light")
    }

    @Test fun fiveTasksShowOnlyThreeAndTwoMore() {
        seed(5)
        showCard(dark = true)
        compose.onNodeWithText("5 项未完成").assertDoesNotExist()
        (1..3).forEach { compose.onNodeWithText("待办 $it").assertIsDisplayed() }
        compose.onNodeWithText("待办 4").assertDoesNotExist()
        compose.onNodeWithText("待办 5").assertDoesNotExist()
        compose.onNodeWithText("还有 2 项").assertIsDisplayed()
        capture("five-dark")
    }

    @Test fun completingPreviewTaskUpdatesOverflowAndPromotesNextInOrder() {
        seed(5)
        showCard()
        compose.onNodeWithContentDescription("完成状态：待办 1").performClick()
        compose.onNodeWithText("4 项未完成").assertDoesNotExist()
        compose.onNodeWithText("待办 1").assertDoesNotExist()
        compose.onNodeWithText("待办 4").assertIsDisplayed()
        compose.onNodeWithText("还有 1 项").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dao.rows.value.first().isCompleted) }
    }

    @Test fun completingLastTaskReturnsToCompactEmptyState() {
        seed(1)
        showCard()
        compose.onNodeWithContentDescription("完成状态：待办 1").performClick()
        compose.onNodeWithText("0 项未完成").assertDoesNotExist()
        compose.onNodeWithText("写下一件想完成的事").assertIsDisplayed()
        compose.onNodeWithText("＋ 添加待办").assertIsDisplayed()
    }

    @Test fun addingUsesExistingInputAndRefreshesCard() {
        showCard()
        compose.onNodeWithText("＋ 添加待办").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("读一章书")
        compose.onNodeWithText("添加", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1 项未完成").assertDoesNotExist()
        compose.onNodeWithText("读一章书", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals("读一章书", dao.rows.value.single().title) }
    }

    @Test fun carriedTaskRemainsAheadOfTodayTask(): Unit = runBlocking {
        dao.insertTodo(TodoEntity(title = "昨日未完成", createdAtMillis = 0,
            createdUsageDay = "2026-10-29"))
        seed(3)
        showCard()
        val old = compose.onNodeWithText("昨日未完成").fetchSemanticsNode().boundsInRoot
        val today = compose.onNodeWithText("待办 1").fetchSemanticsNode().boundsInRoot
        assertTrue(old.bottom <= today.top)
        compose.onNodeWithText("待办 3").assertDoesNotExist()
        compose.onNodeWithText("还有 1 项").assertIsDisplayed()
    }

    @Test fun fullTodoPageStillCompletesRestoresDeletesAndAdds() {
        seed(1)
        compose.setContent { TimeManagerTheme { Surface { TodoScreen(viewModel, {}) } } }
        compose.onNodeWithContentDescription("完成状态：待办 1").performClick()
        compose.onNodeWithText("已完成 1 ⌄").performClick()
        compose.onNodeWithContentDescription("完成状态：待办 1").assertIsOn().performClick()
        compose.onNodeWithContentDescription("完成状态：待办 1").assertIsOff()
        compose.onNodeWithContentDescription("待办选项：待办 1").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除这项待办？").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("待办 1").assertDoesNotExist()
        compose.onNodeWithText("＋ 添加待办").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("新的待办")
        compose.onNodeWithText("添加", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("完成状态：新的待办").assertIsOff()
    }

    @Test fun longTitlesOnNarrowLargeFontCardKeepActionsReachable() {
        runBlocking { repeat(3) { repository.create("很长的待办标题需要合理截断而且不能遮挡复选框和查看全部等操作".repeat(3) + it) } }
        showCard(dark = true, width = 280, fontScale = 2f, viewportHeight = 480)
        compose.onNodeWithText("查看全部").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("3 项未完成").assertDoesNotExist()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("今日待办").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(1, titleLayouts.single().lineCount)
        capture("narrow-large-font-dark")
        dao.rows.value.forEach { todo ->
            compose.onNodeWithContentDescription("完成状态：${todo.title}").performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(todo.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2, layouts.single().lineCount)
            assertTrue(layouts.single().isLineEllipsized(1))
        }
        compose.onNodeWithText("＋ 添加待办").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("新待办")
        compose.onNodeWithText("添加", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun dashboardKeepsUsageTodosModesAndAppOrder() {
        compose.setContent {
            TimeManagerTheme { Surface { HomeDashboard(
                todoViewModel = viewModel, onOpenTodos = {},
                appUsages = listOf(AppUsageWithLimit("test.app", "受管理应用", null, 2_400, 3_600, 0, 3_600)),
                permissionHealth = PermissionHealthState(true, true, true),
                onOpenPermissionSettings = {}, onOpenManagement = {}, onOpenAppDetail = {},
                onOpenStatistics = {}, focusState = FocusModeState(), onOpenFocus = {}, onOpenDeepFocus = {},
            ) } }
        }
        compose.onNodeWithText("写下一件想完成的事").assertIsDisplayed()
        compose.onNodeWithText("0 项未完成").assertDoesNotExist()
        capture("dashboard-empty-light")
        seed(1)
        compose.onNodeWithText("待办 1").assertIsDisplayed()
        val usage = compose.onNodeWithText("今日已使用").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val todos = compose.onNodeWithText("今日待办").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithText("专注模式").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(usage.bottom < todos.top && todos.bottom < mode.top)
        capture("dashboard-one-light")
        runBlocking {
            repository.create("待办 2")
            repository.create("待办 3")
        }
        (1..3).forEach { compose.onNodeWithText("待办 $it").assertIsDisplayed() }
        capture("dashboard-three-light")
        compose.onNodeWithText("受管理应用").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("管理").performScrollTo().assertIsDisplayed()
    }

    @Test fun darkDashboardShowsThreeTodosAboveModesAndKeepsEmptyAppAction() {
        seed(3)
        compose.setContent {
            TimeManagerTheme(themeMode = ThemeMode.DARK) { Surface { HomeDashboard(
                todoViewModel = viewModel, onOpenTodos = {}, appUsages = emptyList(),
                permissionHealth = PermissionHealthState(true, true, true),
                onOpenPermissionSettings = {}, onOpenManagement = {}, onOpenAppDetail = {},
                onOpenStatistics = {}, focusState = FocusModeState(), onOpenFocus = {}, onOpenDeepFocus = {},
            ) } }
        }
        compose.onNodeWithText("今日已使用").assertIsDisplayed()
        (1..3).forEach { compose.onNodeWithText("待办 $it").assertIsDisplayed() }
        capture("dashboard-three-dark")
        compose.onNodeWithText("专注模式").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("静心模式").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("选择应用").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun loadingErrorAndPermissionFallbackKeepTodosAndModesScrollable() {
        seed(3)
        var message by mutableStateOf("正在读取今日使用情况…")
        compose.setContent {
            TimeManagerTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Surface(Modifier.width(320.dp)) { FocusHomeFallback(
                        permissionHealth = PermissionHealthState(false, true, true),
                        todoViewModel = viewModel, onOpenTodos = {}, focusState = FocusModeState(),
                        onOpenFocus = {}, onOpenDeepFocus = {}, onOpenSettings = {}, message = message,
                    ) }
                }
            }
        }
        compose.onNodeWithText("今日已使用").assertIsDisplayed()
        compose.onNodeWithText("今日待办").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { message = "读取使用时间失败：测试错误" }
        compose.onNodeWithText("读取使用时间失败：测试错误").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { message = "使用情况访问未开启，使用统计、连续提醒和每日额度判断暂不可用。" }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("受管理应用总使用时间").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        capture("dashboard-permission-large-font-light")
        compose.onNodeWithText("＋ 添加待办").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("专注模式").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("静心模式").performScrollTo().assertIsDisplayed()
    }
}

/** In-memory test data only; production TodoRepository/ViewModel perform all mutations. */
private class HomeTodoDao : TodoDao {
    val rows = MutableStateFlow<List<TodoEntity>>(emptyList())
    override suspend fun insertTodo(todo: TodoEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value += todo.copy(id = id)
        return id
    }
    override suspend fun markCompleted(id: Long, completedAt: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(completedAtMillis = completedAt) else it }
    }
    override suspend fun markUncompleted(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(completedAtMillis = null) else it }
    }
    override suspend fun deleteTodo(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override fun observeUnfinishedTodos() = rows.map { it.filterNot(TodoEntity::isCompleted) }
    override fun observeCompletedTodosForCurrentDay(startMillis: Long, endMillis: Long) = rows.map { all ->
        all.filter { (it.completedAtMillis ?: -1) in startMillis until endMillis }
    }
    override fun observeCarriedTodos(day: String) = rows.map { all -> all.filter { !it.isCompleted && it.createdUsageDay < day } }
    override fun observeTodayTodos(day: String) = rows.map { all -> all.filter { !it.isCompleted && it.createdUsageDay == day } }
    override suspend fun getUnfinishedTodosSnapshot() = rows.value.filterNot(TodoEntity::isCompleted)
    override suspend fun claimReminder(receipt: TodoReminderReceiptEntity) = 1L
}
