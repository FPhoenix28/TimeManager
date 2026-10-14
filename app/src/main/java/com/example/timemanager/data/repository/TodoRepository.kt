package com.example.timemanager.data.repository

import com.example.timemanager.data.local.TodoDao
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.data.local.TodoReminderReceiptEntity
import com.example.timemanager.domain.todo.TodoDayState
import com.example.timemanager.domain.todo.TodoReminderStage
import com.example.timemanager.domain.todo.validatedTodoTitle
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoRepository(
    private val dao: TodoDao,
    days: Flow<UsageDayProvider.UsageDay>,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val dayProvider: () -> UsageDayProvider = { UsageDayProvider() },
) {
    val unfinishedTodos = dao.observeUnfinishedTodos().distinctUntilChanged()
    val dayState: StateFlow<TodoDayState> = days.flatMapLatest { day ->
        combine(unfinishedTodos, dao.observeCompletedTodosForCurrentDay(day.startTimeMillis, day.endTimeMillis)) {
            unfinished, completed -> TodoDayState.from(unfinished, completed, day.key)
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, TodoDayState())

    suspend fun create(title: String): Long = dao.insertTodo(TodoEntity(
        title = validatedTodoTitle(title), createdAtMillis = now(),
        createdUsageDay = dayProvider().currentUsageDay().key,
    ))
    suspend fun complete(id: Long) = dao.markCompleted(id, now())
    suspend fun uncomplete(id: Long) = dao.markUncompleted(id)
    suspend fun delete(id: Long) = dao.deleteTodo(id)
    suspend fun getUnfinishedSnapshot(): List<TodoEntity> = dao.getUnfinishedTodosSnapshot()
    suspend fun claimReminder(day: String, stage: TodoReminderStage): Boolean =
        dao.claimReminder(TodoReminderReceiptEntity(day, stage.name, now())) != -1L
}
