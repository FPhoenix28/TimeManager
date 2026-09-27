package com.example.timemanager.data.repository

import com.example.timemanager.data.local.PackageUsageTotal
import com.example.timemanager.data.local.UsageHistoryDao
import com.example.timemanager.data.local.UsageHistoryEntity
import kotlinx.coroutines.flow.Flow

class UsageHistoryRepository(
    private val dao: UsageHistoryDao,
) {
    suspend fun saveSnapshot(packageName: String, usageDate: String, usageSeconds: Long) {
        require(packageName.isNotBlank())
        require(usageDate.isNotBlank())
        require(usageSeconds >= 0L)
        val key = packageName to usageDate
        synchronized(SNAPSHOT_LOCK) {
            if ((snapshotHighWaterMarks[key] ?: -1L) >= usageSeconds) return
        }
        dao.upsertSnapshot(packageName, usageDate, usageSeconds)
        synchronized(SNAPSHOT_LOCK) {
            snapshotHighWaterMarks[key] = maxOf(
                snapshotHighWaterMarks[key] ?: -1L,
                usageSeconds,
            )
        }
    }

    fun observeAppHistory(packageName: String, startDate: String, endDate: String):
        Flow<List<UsageHistoryEntity>> = dao.observeAppHistory(packageName, startDate, endDate)

    fun observePackageTotals(startDate: String, endDate: String):
        Flow<List<PackageUsageTotal>> = dao.observePackageTotals(startDate, endDate)

    fun observeHistoryBetween(startDate: String, endDate: String):
        Flow<List<UsageHistoryEntity>> = dao.observeHistoryBetween(startDate, endDate)

    suspend fun getHistoryBetween(startDate: String, endDate: String):
        List<UsageHistoryEntity> = dao.getHistoryBetween(startDate, endDate)

    suspend fun getDay(usageDate: String) = dao.getDay(usageDate)

    suspend fun getAppTotal(packageName: String, startDate: String, endDate: String): Long =
        dao.getAppTotal(packageName, startDate, endDate)

    suspend fun getManagedTotal(
        packageNames: Set<String>,
        startDate: String,
        endDate: String,
    ): Long = if (packageNames.isEmpty()) 0L else {
        dao.getManagedTotal(packageNames, startDate, endDate)
    }

    suspend fun clearAll() {
        dao.clearAll()
        synchronized(SNAPSHOT_LOCK) { snapshotHighWaterMarks.clear() }
    }

    private companion object {
        val SNAPSHOT_LOCK = Any()
        val snapshotHighWaterMarks = mutableMapOf<Pair<String, String>, Long>()
    }
}
