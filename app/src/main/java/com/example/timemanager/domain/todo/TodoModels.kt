package com.example.timemanager.domain.todo

import com.example.timemanager.data.local.TodoEntity

const val TODO_TITLE_MAX_LENGTH = 160

fun validatedTodoTitle(title: String): String = title.trim().also {
    require(it.isNotEmpty()) { "请写下要做的事情" }
    require(it.length <= TODO_TITLE_MAX_LENGTH) { "标题请控制在 $TODO_TITLE_MAX_LENGTH 字以内" }
}

data class TodoSummaryItem(val id: Long, val title: String)
data class TodoSummary(val totalCount: Int = 0, val topItems: List<TodoSummaryItem> = emptyList()) {
    val remainingCount: Int get() = (totalCount - topItems.size).coerceAtLeast(0)
}

data class TodoDayState(
    val usageDay: String = "",
    val carried: List<TodoEntity> = emptyList(),
    val today: List<TodoEntity> = emptyList(),
    val completed: List<TodoEntity> = emptyList(),
) {
    val unfinished: List<TodoEntity> get() = carried + today
    val summary: TodoSummary get() = TodoSummary(
        unfinished.size, unfinished.take(3).map { TodoSummaryItem(it.id, it.title) },
    )

    companion object {
        fun from(unfinished: List<TodoEntity>, completed: List<TodoEntity>, day: String): TodoDayState {
            val sorted = unfinished.filterNot { it.isCompleted }
                .sortedWith(compareBy<TodoEntity> { it.createdAtMillis }.thenBy { it.id })
            return TodoDayState(day, sorted.filter { it.createdUsageDay < day },
                sorted.filter { it.createdUsageDay == day }, completed)
        }
    }
}
