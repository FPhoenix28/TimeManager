package com.example.timemanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UsageHistoryEntity::class, TodoEntity::class, TodoReminderReceiptEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageHistoryDao(): UsageHistoryDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "timemanager.db",
            ).addMigrations(TodoMigration.MIGRATION_1_2).build().also { instance = it }
        }
    }
}
