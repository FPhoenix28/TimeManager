package com.example.timemanager.domain.todo

import com.example.timemanager.data.local.TodoEntity
import org.junit.Assert.*
import org.junit.Test

class TodoModelsTest {
    private fun todo(id: Long, day: String, created: Long = id) = TodoEntity(
        id = id, title = "任务$id", createdAtMillis = created, createdUsageDay = day,
    )

    @Test fun trimsTitles() { assertEquals("看论文", validatedTodoTitle(" 看论文 ")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsBlank() { validatedTodoTitle("   ") }
    @Test(expected = IllegalArgumentException::class) fun rejectsLongTitle() { validatedTodoTitle("a".repeat(161)) }
    @Test fun carriesOriginalRecordWithoutCopyAndSortsOldestFirst() {
        val old = todo(1, "2026-10-09")
        val today = todo(2, "2026-10-12")
        val state = TodoDayState.from(listOf(today, old), emptyList(), "2026-10-12")
        assertEquals(listOf(old), state.carried)
        assertEquals(listOf(today), state.today)
        assertEquals(listOf(1L, 2L), state.unfinished.map { it.id })
        assertEquals("2026-10-09", state.carried.single().createdUsageDay)
    }
    @Test fun completionAndUndoRetainCreationDate() {
        val old = todo(1, "2026-10-09")
        val completed = old.copy(completedAtMillis = 123L)
        assertTrue(completed.isCompleted)
        val state = TodoDayState.from(listOf(completed.copy(completedAtMillis = null)), emptyList(), "2026-10-12")
        assertEquals(1L, state.carried.single().id)
    }
    @Test fun summaryHasOnlyThreeAndRemainingCount() {
        val state = TodoDayState.from((1L..5L).map { todo(it, "2026-10-12") }, emptyList(), "2026-10-12")
        assertEquals(5, state.summary.totalCount)
        assertEquals(3, state.summary.topItems.size)
        assertEquals(2, state.summary.remainingCount)
        assertEquals(0, TodoDayState().summary.totalCount)
    }
}
