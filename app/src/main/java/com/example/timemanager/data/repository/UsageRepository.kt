package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppUsage

/** Provides app-usage data to the presentation layer. */
class UsageRepository(
    private val usageStatsReader: UsageStatsReader,
) {
    fun getTodayUsage(): List<AppUsage> = usageStatsReader.getTodayUsage()
}
