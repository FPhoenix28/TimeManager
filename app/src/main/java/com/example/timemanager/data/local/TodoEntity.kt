package com.example.timemanager.data.local

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtMillis: Long,
    /** Original ISO date of the existing 04:00 usage day; never rewritten on carry-over. */
    val createdUsageDay: String,
    val completedAtMillis: Long? = null,
) {
    @get:Ignore val isCompleted: Boolean get() = completedAtMillis != null
}

@Entity(tableName = "todo_reminder_receipts", primaryKeys = ["usageDay", "stage"])
data class TodoReminderReceiptEntity(
    val usageDay: String,
    val stage: String,
    val sentAtMillis: Long,
)
