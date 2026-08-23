package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageHistoryDao {
    @Query(
        """INSERT INTO usage_history(packageName, usageDate, usageSeconds)
        VALUES(:packageName, :usageDate, :usageSeconds)
        ON CONFLICT(packageName, usageDate) DO UPDATE SET
        usageSeconds = MAX(usage_history.usageSeconds, excluded.usageSeconds)""",
    )
    suspend fun upsertSnapshot(packageName: String, usageDate: String, usageSeconds: Long)

    @Query(
        """SELECT * FROM usage_history
        WHERE packageName = :packageName AND usageDate BETWEEN :startDate AND :endDate
        ORDER BY usageDate DESC""",
    )
    fun observeAppHistory(
        packageName: String,
        startDate: String,
        endDate: String,
    ): Flow<List<UsageHistoryEntity>>

    @Query(
        """SELECT packageName, SUM(usageSeconds) AS usageSeconds FROM usage_history
        WHERE usageDate BETWEEN :startDate AND :endDate
        GROUP BY packageName ORDER BY usageSeconds DESC""",
    )
    fun observePackageTotals(
        startDate: String,
        endDate: String,
    ): Flow<List<PackageUsageTotal>>

    @Query(
        """SELECT * FROM usage_history
        WHERE usageDate BETWEEN :startDate AND :endDate
        ORDER BY usageDate ASC""",
    )
    fun observeHistoryBetween(
        startDate: String,
        endDate: String,
    ): Flow<List<UsageHistoryEntity>>

    @Query("SELECT * FROM usage_history WHERE usageDate = :usageDate ORDER BY usageSeconds DESC")
    suspend fun getDay(usageDate: String): List<UsageHistoryEntity>

    @Query(
        """SELECT COALESCE(SUM(usageSeconds), 0) FROM usage_history
        WHERE packageName = :packageName AND usageDate BETWEEN :startDate AND :endDate""",
    )
    suspend fun getAppTotal(packageName: String, startDate: String, endDate: String): Long

    @Query(
        """SELECT COALESCE(SUM(usageSeconds), 0) FROM usage_history
        WHERE packageName IN (:packageNames) AND usageDate BETWEEN :startDate AND :endDate""",
    )
    suspend fun getManagedTotal(
        packageNames: Set<String>,
        startDate: String,
        endDate: String,
    ): Long

    @Query("DELETE FROM usage_history")
    suspend fun clearAll()
}
