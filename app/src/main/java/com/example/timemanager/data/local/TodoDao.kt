package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Insert suspend fun insertTodo(todo: TodoEntity): Long

    @Query("UPDATE todos SET completedAtMillis = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)

    @Query("UPDATE todos SET completedAtMillis = NULL WHERE id = :id")
    suspend fun markUncompleted(id: Long)

    @Query("DELETE FROM todos WHERE id = :id") suspend fun deleteTodo(id: Long)

    @Query("SELECT * FROM todos WHERE completedAtMillis IS NULL ORDER BY createdAtMillis ASC, id ASC")
    fun observeUnfinishedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE completedAtMillis >= :startMillis AND completedAtMillis < :endMillis ORDER BY completedAtMillis DESC, id DESC")
    fun observeCompletedTodosForCurrentDay(startMillis: Long, endMillis: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE completedAtMillis IS NULL AND createdUsageDay < :day ORDER BY createdAtMillis ASC, id ASC")
    fun observeCarriedTodos(day: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE completedAtMillis IS NULL AND createdUsageDay = :day ORDER BY createdAtMillis ASC, id ASC")
    fun observeTodayTodos(day: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE completedAtMillis IS NULL ORDER BY createdUsageDay ASC, createdAtMillis ASC, id ASC")
    suspend fun getUnfinishedTodosSnapshot(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claimReminder(receipt: TodoReminderReceiptEntity): Long
}
