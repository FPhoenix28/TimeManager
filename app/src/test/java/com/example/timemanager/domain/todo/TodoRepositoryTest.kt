package com.example.timemanager.domain.todo

import com.example.timemanager.data.local.*
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.utils.UsageDayProvider
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class TodoRepositoryTest {
    private val clock = Clock.fixed(OffsetDateTime.parse("2026-10-11T14:30:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
    private val provider = UsageDayProvider(clock)
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun repository(dao: FakeTodoDao, days: Flow<UsageDayProvider.UsageDay>, scope: CoroutineScope) =
        TodoRepository(dao, days, scope, clock::millis, { provider })

    @Test fun createCompleteRestoreAndDeleteUseSameId() = runBlocking {
        val scope = scope()
        try {
            val dao = FakeTodoDao()
            val repository = repository(dao, flowOf(provider.currentUsageDay()), scope)
            val id = repository.create(" 概率论作业 ")
            assertEquals("概率论作业", dao.rows.value.single().title)
            repository.complete(id)
            assertTrue(repository.getUnfinishedSnapshot().isEmpty())
            repository.uncomplete(id)
            assertEquals(id, repository.getUnfinishedSnapshot().single().id)
            repository.delete(id)
            assertTrue(dao.rows.value.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun boundaryRegroupsOriginalRecordWithoutInserting() = runBlocking {
        val scope = scope()
        try {
            val dao = FakeTodoDao()
            val days = MutableStateFlow(provider.currentUsageDay())
            val repository = repository(dao, days, scope)
            val id = repository.create("跑步")
            assertEquals(id, repository.dayState.first { it.today.isNotEmpty() }.today.single().id)
            days.value = provider.usageDay(java.time.LocalDate.parse("2026-10-14"))
            assertEquals(id, repository.dayState.first { it.carried.isNotEmpty() }.carried.single().id)
            assertEquals(1, dao.rows.value.size)
        } finally { scope.cancel() }
    }
    @Test fun workerReloadsFreshSnapshotAndDeduplicatesAcrossRepositoryInstances() = runBlocking {
        val scope = scope()
        try {
            val dao = FakeTodoDao()
            val days = flowOf(provider.currentUsageDay())
            val repo = repository(dao, days, scope)
            val first = repo.create("作业")
            repo.create("跑步")
            repo.complete(first)
            val sentCounts = mutableListOf<Int>()
            fun runner(repo: TodoRepository) = TodoReminderRunner(repo, TodoReminderPolicy(clock), { true }) { _, todos -> sentCounts += todos.size }
            assertTrue(runner(repo).run("2026-10-11", TodoReminderStage.AFTERNOON))
            assertFalse(runner(repository(dao, days, scope)).run("2026-10-11", TodoReminderStage.AFTERNOON))
            assertEquals(listOf(1), sentCounts)
        } finally { scope.cancel() }
    }
    @Test fun noNotificationForEmptyOrUnpermittedOrWrongStage() = runBlocking {
        val scope = scope()
        try {
            val dao = FakeTodoDao()
            val repo = repository(dao, flowOf(provider.currentUsageDay()), scope)
            var sends = 0
            val runner = TodoReminderRunner(repo, TodoReminderPolicy(clock), { true }) { _, _ -> sends++ }
            assertFalse(runner.run("2026-10-11", TodoReminderStage.AFTERNOON))
            repo.create("论文")
            assertFalse(runner.run("2026-10-11", TodoReminderStage.EVENING))
            val unpermitted = TodoReminderRunner(repo, TodoReminderPolicy(clock), { false }) { _, _ -> sends++ }
            assertFalse(unpermitted.run("2026-10-11", TodoReminderStage.AFTERNOON))
            assertEquals(0, sends)
            assertTrue(dao.receipts.isEmpty())
        } finally { scope.cancel() }
    }
}

private class FakeTodoDao : TodoDao {
    val rows = MutableStateFlow<List<TodoEntity>>(emptyList())
    val receipts = mutableSetOf<Pair<String, String>>()
    private var nextId = 0L
    override suspend fun insertTodo(todo: TodoEntity): Long {
        val id = ++nextId
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
    override suspend fun getUnfinishedTodosSnapshot() = rows.value.filterNot(TodoEntity::isCompleted).sortedBy { it.createdAtMillis }
    override suspend fun claimReminder(receipt: TodoReminderReceiptEntity) = if (receipts.add(receipt.usageDay to receipt.stage)) 1L else -1L
}
