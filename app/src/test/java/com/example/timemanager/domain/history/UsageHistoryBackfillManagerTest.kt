package com.example.timemanager.domain.history

import com.example.timemanager.data.local.UsageHistoryEntity
import com.example.timemanager.data.model.AppUsage
import com.example.timemanager.data.repository.UsageStatsQueryResult
import com.example.timemanager.utils.UsageDayProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryBackfillManagerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-09-09T04:00:00Z"), zone)
    private val usageDayProvider = UsageDayProvider(clock)

    @Test
    fun `missing usage day is queried and zero usage is stored explicitly`() = runBlocking {
        val queriedDates = mutableListOf<LocalDate>()
        val writes = linkedMapOf<Pair<String, String>, Long>()
        val existing = listOf(
            record("app.one", "2026-09-07", 100L),
            record("app.two", "2026-09-07", 200L),
            record("app.one", "2026-09-09", 10L),
            record("app.two", "2026-09-09", 20L),
        )
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { true },
            trackedPackages = { setOf("app.one", "app.two") },
            getHistoryBetween = { _, _ -> existing },
            queryUsageBetween = { startTimeMillis, _ ->
                val usageDate = Instant.ofEpochMilli(startTimeMillis)
                    .atZone(zone)
                    .toLocalDate()
                queriedDates += usageDate
                UsageStatsQueryResult.Success(
                    if (usageDate == LocalDate.parse("2026-09-08")) {
                        listOf(usage("app.one", 3_120L))
                    } else {
                        emptyList()
                    },
                )
            },
            saveSnapshot = { packageName, usageDate, usageSeconds ->
                writes[packageName to usageDate] = usageSeconds
            },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { 1_000L },
            backfillDays = 3,
            throttleMillis = 60_000L,
            log = {},
        )

        manager.backfillIfNeeded()

        assertEquals(
            listOf(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-09")),
            queriedDates,
        )
        assertEquals(3_120L, writes["app.one" to "2026-09-08"])
        assertEquals(0L, writes["app.two" to "2026-09-08"])
        assertEquals(0L, writes["app.one" to "2026-09-09"])
        assertEquals(0L, writes["app.two" to "2026-09-09"])
    }

    @Test
    fun `a successful backfill is throttled and never accumulates snapshots`() = runBlocking {
        var queryCount = 0
        var elapsedTimeMillis = 1_000L
        val writes = mutableListOf<Long>()
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { true },
            trackedPackages = { setOf("app.one") },
            getHistoryBetween = { _, _ ->
                listOf(
                    record("app.one", "2026-09-07", 100L),
                    record("app.one", "2026-09-08", 200L),
                    record("app.one", "2026-09-09", 300L),
                )
            },
            queryUsageBetween = { _, _ ->
                queryCount += 1
                UsageStatsQueryResult.Success(listOf(usage("app.one", 300L)))
            },
            saveSnapshot = { _, _, usageSeconds -> writes += usageSeconds },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { elapsedTimeMillis },
            backfillDays = 3,
            throttleMillis = 60_000L,
            log = {},
        )

        manager.backfillIfNeeded()
        val second = manager.backfillIfNeeded()
        elapsedTimeMillis += 60_000L
        manager.backfillIfNeeded()

        assertEquals(2, queryCount)
        assertEquals(listOf(300L, 300L), writes)
        assertTrue(second is UsageHistoryBackfillResult.Throttled)
    }

    @Test
    fun `missing permission leaves existing history untouched`() = runBlocking {
        var queried = false
        var saved = false
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { false },
            trackedPackages = { setOf("app.one") },
            getHistoryBetween = { _, _ -> emptyList() },
            queryUsageBetween = { _, _ ->
                queried = true
                UsageStatsQueryResult.Success(emptyList())
            },
            saveSnapshot = { _, _, _ -> saved = true },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { 1_000L },
            backfillDays = 3,
            throttleMillis = 60_000L,
            log = {},
        )

        val result = manager.backfillIfNeeded()

        assertTrue(result is UsageHistoryBackfillResult.NoUsageAccess)
        assertTrue(!queried)
        assertTrue(!saved)
    }

    @Test
    fun `three missed days are recovered without querying an existing ended day`() = runBlocking {
        val queriedDates = mutableListOf<LocalDate>()
        val writes = mutableListOf<Pair<String, String>>()
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { true },
            trackedPackages = { setOf("app.one") },
            getHistoryBetween = { _, _ ->
                listOf(record("app.one", "2026-09-05", 100L))
            },
            queryUsageBetween = { start, _ ->
                queriedDates += Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                UsageStatsQueryResult.Success(listOf(usage("app.one", 600L)))
            },
            saveSnapshot = { packageName, usageDate, _ ->
                writes += packageName to usageDate
            },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { 1_000L },
            backfillDays = 5,
            throttleMillis = 60_000L,
            log = {},
        )

        manager.backfillIfNeeded()

        assertEquals(
            listOf("2026-09-06", "2026-09-07", "2026-09-08", "2026-09-09"),
            queriedDates.map(LocalDate::toString),
        )
        assertEquals(queriedDates.map(LocalDate::toString), writes.map(Pair<String, String>::second))
    }

    @Test
    fun `a missing app is filled even when another app already has that day`() = runBlocking {
        val writes = mutableMapOf<Pair<String, String>, Long>()
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { true },
            trackedPackages = { setOf("app.one", "app.two") },
            getHistoryBetween = { _, _ ->
                listOf(
                    record("app.one", "2026-09-08", 120L),
                    record("app.one", "2026-09-09", 120L),
                    record("app.two", "2026-09-09", 0L),
                )
            },
            queryUsageBetween = { _, _ ->
                UsageStatsQueryResult.Success(listOf(usage("app.two", 300L)))
            },
            saveSnapshot = { packageName, usageDate, seconds ->
                writes[packageName to usageDate] = seconds
            },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { 1_000L },
            backfillDays = 2,
            throttleMillis = 60_000L,
            log = {},
        )

        manager.backfillIfNeeded()

        assertEquals(300L, writes["app.two" to "2026-09-08"])
    }

    @Test
    fun `unavailable system day stays missing and can be retried`() = runBlocking {
        var queryCount = 0
        var elapsedTimeMillis = 1_000L
        val writes = mutableListOf<Long>()
        val manager = UsageHistoryBackfillManager(
            hasUsageAccess = { true },
            trackedPackages = { setOf("app.one") },
            getHistoryBetween = { _, _ ->
                listOf(record("app.one", "2026-09-09", 60L))
            },
            queryUsageBetween = { _, _ ->
                queryCount++
                UsageStatsQueryResult.Unavailable
            },
            saveSnapshot = { _, _, seconds -> writes += seconds },
            usageDayProvider = usageDayProvider,
            currentTimeMillis = { clock.millis() },
            elapsedRealtimeMillis = { elapsedTimeMillis },
            backfillDays = 2,
            throttleMillis = 60_000L,
            log = {},
        )

        manager.backfillIfNeeded()
        manager.backfillIfNeeded()
        assertEquals(2, queryCount)
        elapsedTimeMillis += 60_000L
        manager.backfillIfNeeded()

        assertEquals(4, queryCount)
        assertTrue(writes.isEmpty())
    }

    private fun record(packageName: String, date: String, seconds: Long) =
        UsageHistoryEntity(packageName, date, seconds)

    private fun usage(packageName: String, seconds: Long) = AppUsage(
        appName = packageName,
        packageName = packageName,
        usageTimeMillis = seconds * 1_000L,
        appIcon = null,
    )
}
