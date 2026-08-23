package com.example.timemanager.domain.statistics

import com.example.timemanager.data.local.UsageHistoryEntity
import java.time.LocalDate

data class DailyUsagePoint(
    val date: String,
    val usageSeconds: Long,
)

data class PeriodAnalysis(
    val totalUsageSeconds: Long,
    val averageUsageSeconds: Long,
    val previousPeriodUsageSeconds: Long,
    /** Percentage change; null means the previous period had no comparable usage. */
    val changePercent: Double?,
    val topPackageName: String?,
    val packageTotals: Map<String, Long>,
    val dailyPoints: List<DailyUsagePoint>,
)

class StatisticsCalculator {
    fun calculateManagedApps(
        records: List<UsageHistoryEntity>,
        managedPackages: Set<String>,
        currentEndDate: LocalDate,
        days: Long,
    ): PeriodAnalysis = calculate(
        records = records.filter { it.packageName in managedPackages },
        currentEndDate = currentEndDate,
        days = days,
    )

    fun calculateApp(
        records: List<UsageHistoryEntity>,
        packageName: String,
        currentEndDate: LocalDate,
        days: Long,
    ): PeriodAnalysis = calculate(
        records = records.filter { it.packageName == packageName },
        currentEndDate = currentEndDate,
        days = days,
    )

    private fun calculate(
        records: List<UsageHistoryEntity>,
        currentEndDate: LocalDate,
        days: Long,
    ): PeriodAnalysis {
        require(days > 0L)
        val currentStart = currentEndDate.minusDays(days - 1L)
        val previousEnd = currentStart.minusDays(1L)
        val previousStart = previousEnd.minusDays(days - 1L)

        val currentRecords = records.filter { record ->
            LocalDate.parse(record.usageDate) in currentStart..currentEndDate
        }
        val previousRecords = records.filter { record ->
            LocalDate.parse(record.usageDate) in previousStart..previousEnd
        }
        val packageTotals = currentRecords
            .groupBy(UsageHistoryEntity::packageName)
            .mapValues { (_, values) -> values.sumOf(UsageHistoryEntity::usageSeconds) }
        val secondsByDate = currentRecords
            .groupBy(UsageHistoryEntity::usageDate)
            .mapValues { (_, values) -> values.sumOf(UsageHistoryEntity::usageSeconds) }
        val dailyPoints = (0 until days.toInt()).map { offset ->
            val date = currentStart.plusDays(offset.toLong())
            DailyUsagePoint(
                date = date.toString(),
                usageSeconds = secondsByDate[date.toString()] ?: 0L,
            )
        }
        val currentTotal = dailyPoints.sumOf(DailyUsagePoint::usageSeconds)
        val previousTotal = previousRecords.sumOf(UsageHistoryEntity::usageSeconds)
        return PeriodAnalysis(
            totalUsageSeconds = currentTotal,
            averageUsageSeconds = currentTotal / days,
            previousPeriodUsageSeconds = previousTotal,
            changePercent = if (previousTotal == 0L) null else {
                (currentTotal - previousTotal).toDouble() / previousTotal.toDouble() * 100.0
            },
            topPackageName = packageTotals.maxByOrNull { it.value }?.key,
            packageTotals = packageTotals,
            dailyPoints = dailyPoints,
        )
    }
}
