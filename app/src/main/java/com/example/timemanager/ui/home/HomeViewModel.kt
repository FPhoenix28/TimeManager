package com.example.timemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import android.util.Log
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.domain.statistics.StatisticsCalculator
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val usageTimeRepository: UsageTimeRepository,
    private val limitRepository: LimitRepository,
    private val interventionRepository: InterventionRepository,
    private val usageHistoryRepository: UsageHistoryRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val usageDayProvider: UsageDayProvider = UsageDayProvider(),
    private val statisticsCalculator: StatisticsCalculator = StatisticsCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun loadTodayUsage() {
        val hasContent = _uiState.value is HomeUiState.Success
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val totalStartedAt = SystemClock.elapsedRealtime()
            if (!hasContent) _uiState.value = HomeUiState.Loading
            val usageStartedAt = SystemClock.elapsedRealtime()
            val appUsages = runCatching {
                withContext(Dispatchers.IO) {
                    usageTimeRepository.getTodayUsage()
                }
            }.getOrElse { error ->
                if (!hasContent) {
                    _uiState.value = HomeUiState.Error(
                        error.message ?: "无法读取今日使用时间",
                    )
                }
                return@launch
            }
            val usageDuration = SystemClock.elapsedRealtime() - usageStartedAt

            val currentEndDate = LocalDate.parse(usageDayProvider.currentUsageDay().key)
            val previousStartDate = currentEndDate.minusDays(13L)
            combine(
                limitRepository.observeAllLimits(),
                trackedAppRepository.observeTrackedPackageNames(),
                usageHistoryRepository.observeHistoryBetween(
                    previousStartDate.toString(),
                    currentEndDate.toString(),
                ),
            ) { limits, tracked, records -> Triple(limits, tracked, records) }
                .catch { error ->
                    if (!hasContent) {
                        _uiState.value = HomeUiState.Error(
                            error.message ?: "无法读取应用额度",
                        )
                    }
                }
                .collect { (appLimits, trackedPackages, historyRecords) ->
                    val modelStartedAt = SystemClock.elapsedRealtime()
                    val newState = withContext(Dispatchers.IO) {
                        val limitsByPackage = appLimits.associateBy(AppLimit::packageName)
                        val usageDayKey = usageDayProvider.currentUsageDay().key
                        val managedApps = trackedPackages.mapNotNull(installedAppRepository::getUserInstalledApp)
                        val usageWithLimits = managedHomeUsage(appUsages, managedApps).map { appUsage ->
                            val baseLimit = limitsByPackage[appUsage.packageName]?.limitSeconds
                            val extensionSeconds = if (baseLimit == null) {
                                0L
                            } else {
                                interventionRepository.getState(
                                    appUsage.packageName,
                                    usageDayKey,
                                ).todayExtensionSeconds
                            }
                            val baseLimitSeconds = baseLimit
                            AppUsageWithLimit(
                                packageName = appUsage.packageName,
                                appName = appUsage.appName,
                                appIcon = appUsage.appIcon,
                                usedSeconds = appUsage.usageTimeMillis / MILLIS_PER_SECOND,
                                baseLimitSeconds = baseLimitSeconds,
                                extensionSeconds = extensionSeconds,
                                effectiveLimitSeconds = baseLimitSeconds?.plus(extensionSeconds),
                            )
                        }.sortedByDescending(AppUsageWithLimit::usedMinutes)

                        val weeklyAnalysis = statisticsCalculator.calculateManagedApps(
                            records = historyRecords,
                            managedPackages = trackedPackages,
                            currentEndDate = currentEndDate,
                            days = 7L,
                        )
                        HomeUiState.Success(
                            appUsages = usageWithLimits,
                            weeklyTrend = WeeklyTrendSummary(
                                totalSeconds = weeklyAnalysis.totalUsageSeconds,
                                changePercent = weeklyAnalysis.changePercent,
                            ),
                        )
                    }
                    _uiState.value = newState
                    Log.d(
                        TAG,
                        "UsageStats=${usageDuration}ms " +
                            "Model=${SystemClock.elapsedRealtime() - modelStartedAt}ms " +
                            "Total=${SystemClock.elapsedRealtime() - totalStartedAt}ms",
                    )
                }
        }
    }

    class Factory(
        private val usageTimeRepository: UsageTimeRepository,
        private val limitRepository: LimitRepository,
        private val interventionRepository: InterventionRepository,
        private val usageHistoryRepository: UsageHistoryRepository,
        private val trackedAppRepository: TrackedAppRepository,
        private val installedAppRepository: InstalledAppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    usageTimeRepository,
                    limitRepository,
                    interventionRepository,
                    usageHistoryRepository,
                    trackedAppRepository,
                    installedAppRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
        const val TAG = "HomeLoad"
    }
}
