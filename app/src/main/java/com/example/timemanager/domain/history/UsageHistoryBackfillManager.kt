package com.example.timemanager.domain.history

import android.os.SystemClock
import android.util.Log
import com.example.timemanager.data.local.UsageHistoryEntity
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.data.repository.UsageStatsQueryResult
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.utils.UsageDayProvider
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UsageHistoryBackfillManager internal constructor(
    private val hasUsageAccess: () -> Boolean,
    private val trackedPackages: suspend () -> Set<String>,
    private val getHistoryBetween: suspend (String, String) -> List<UsageHistoryEntity>,
    private val queryUsageBetween: (Long, Long) -> UsageStatsQueryResult,
    private val saveSnapshot: suspend (String, String, Long) -> Unit,
    private val usageDayProvider: UsageDayProvider,
    private val currentTimeMillis: () -> Long,
    private val elapsedRealtimeMillis: () -> Long,
    private val backfillDays: Int,
    private val throttleMillis: Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { message -> Log.d(TAG, message) },
) {
    constructor(
        usageStatsReader: UsageStatsReader,
        historyRepository: UsageHistoryRepository,
        trackedAppRepository: TrackedAppRepository,
        usageDayProvider: UsageDayProvider = UsageDayProvider(),
    ) : this(
        hasUsageAccess = usageStatsReader::hasUsageAccess,
        trackedPackages = { trackedAppRepository.observeTrackedPackageNames().first() },
        getHistoryBetween = historyRepository::getHistoryBetween,
        queryUsageBetween = usageStatsReader::getUsageBetween,
        saveSnapshot = historyRepository::saveSnapshot,
        usageDayProvider = usageDayProvider,
        currentTimeMillis = System::currentTimeMillis,
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
        backfillDays = BACKFILL_DAYS,
        throttleMillis = BACKFILL_THROTTLE_MILLIS,
    )

    private val mutex = Mutex()
    private var lastBackfillAtMillis: Long? = null
    private var lastBackfillUsageDate: LocalDate? = null
    private var lastBackfillPackages: Set<String>? = null

    suspend fun backfillIfNeeded(): UsageHistoryBackfillResult = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasUsageAccess()) {
                lastBackfillAtMillis = null
                log("Backfill skipped: usage access is not granted")
                return@withLock UsageHistoryBackfillResult.NoUsageAccess
            }

            val packages = trackedPackages()
            if (packages.isEmpty()) {
                log("Backfill skipped: no managed apps")
                return@withLock UsageHistoryBackfillResult.NoManagedApps
            }

            val currentUsageDate = LocalDate.parse(usageDayProvider.currentUsageDay().key)
            val elapsedNow = elapsedRealtimeMillis()
            val lastBackfill = lastBackfillAtMillis
            if (
                lastBackfill != null &&
                elapsedNow - lastBackfill < throttleMillis &&
                lastBackfillUsageDate == currentUsageDate &&
                lastBackfillPackages == packages
            ) {
                return@withLock UsageHistoryBackfillResult.Throttled
            }
            val rangeStart = currentUsageDate.minusDays(backfillDays.toLong() - 1L)
            val records = getHistoryBetween(rangeStart.toString(), currentUsageDate.toString())
            val existingKeys = records
                .mapTo(mutableSetOf()) { record -> record.packageName to record.usageDate }
            val existingDates = records.map(UsageHistoryEntity::usageDate).distinct()
            val endedDates = buildList {
                var date = rangeStart
                while (date.isBefore(currentUsageDate)) {
                    add(date)
                    date = date.plusDays(1L)
                }
            }
            val missingDates = endedDates.filter { date ->
                packages.any { packageName ->
                    (packageName to date.toString()) !in existingKeys
                }
            }
            log(
                "Backfill: rangeStart=$rangeStart rangeEnd=$currentUsageDate " +
                    "existingDates=$existingDates missingDates=$missingDates",
            )

            var unavailableDays = 0
            val datesToQuery = missingDates + currentUsageDate
            datesToQuery.forEach { usageDate ->
                val usageDay = usageDayProvider.usageDay(usageDate)
                val queryEnd = minOf(usageDay.endTimeMillis, currentTimeMillis())
                when (
                    val result = queryUsageBetween(
                        usageDay.startTimeMillis,
                        queryEnd,
                    )
                ) {
                    UsageStatsQueryResult.Unavailable -> {
                        unavailableDays += 1
                        log(
                            "BackfillDay unavailable: date=$usageDate " +
                                "queryStart=${usageDay.startTimeMillis} queryEnd=$queryEnd",
                        )
                    }

                    is UsageStatsQueryResult.Success -> {
                        val usageByPackage = result.usages.associate { usage ->
                            usage.packageName to usage.usageTimeMillis / MILLIS_PER_SECOND
                        }
                        packages.forEach { packageName ->
                            val usageSeconds = usageByPackage[packageName] ?: 0L
                            saveSnapshot(packageName, usageDate.toString(), usageSeconds)
                            log(
                                "BackfillDay: date=$usageDate " +
                                    "queryStart=${usageDay.startTimeMillis} queryEnd=$queryEnd " +
                                    "packageName=$packageName usageSeconds=$usageSeconds",
                            )
                        }
                    }
                }
            }

            lastBackfillAtMillis = elapsedNow
            lastBackfillUsageDate = currentUsageDate
            lastBackfillPackages = packages
            UsageHistoryBackfillResult.Completed(
                queriedDays = datesToQuery.size,
                unavailableDays = unavailableDays,
            )
        }
    }

    private companion object {
        const val TAG = "UsageHistoryBackfill"
        const val BACKFILL_DAYS = 30
        const val BACKFILL_THROTTLE_MILLIS = 15L * 60L * 1_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

sealed interface UsageHistoryBackfillResult {
    data class Completed(
        val queriedDays: Int,
        val unavailableDays: Int,
    ) : UsageHistoryBackfillResult

    data object Throttled : UsageHistoryBackfillResult
    data object NoUsageAccess : UsageHistoryBackfillResult
    data object NoManagedApps : UsageHistoryBackfillResult
}
