package com.example.timemanager.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: the existing usage_history table is not rebuilt or modified. */
object TodoMigration {
    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `createdUsageDay` TEXT NOT NULL, `completedAtMillis` INTEGER)",
        "CREATE TABLE IF NOT EXISTS `todo_reminder_receipts` (`usageDay` TEXT NOT NULL, `stage` TEXT NOT NULL, `sentAtMillis` INTEGER NOT NULL, PRIMARY KEY(`usageDay`, `stage`))",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            statements.forEach(db::execSQL)
        }
    }
}
