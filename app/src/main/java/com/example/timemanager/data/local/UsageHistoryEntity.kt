package com.example.timemanager.data.local

import androidx.room.Entity

@Entity(
    tableName = "usage_history",
    primaryKeys = ["packageName", "usageDate"],
)
data class UsageHistoryEntity(
    val packageName: String,
    /** ISO local date representing the 04:00-to-04:00 usage period. */
    val usageDate: String,
    val usageSeconds: Long,
)

data class PackageUsageTotal(
    val packageName: String,
    val usageSeconds: Long,
)
