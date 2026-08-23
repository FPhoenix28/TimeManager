package com.example.timemanager.ui.statistics

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.utils.UsageDayProvider
import com.example.timemanager.domain.statistics.DailyUsagePoint
import com.example.timemanager.domain.statistics.StatisticsCalculator
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data class Error(val message: String) : StatisticsUiState
    data class Success(
        val period: HistoryPeriod,
        val totalSeconds: Long,
        val averageSeconds: Long,
        val previousPeriodSeconds: Long,
        val changePercent: Double?,
        val topApp: StatisticsAppItem?,
        val dailyPoints: List<DailyUsagePoint>,
        val apps: List<StatisticsAppItem>,
    ) : StatisticsUiState
}

data class StatisticsAppItem(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val usageSeconds: Long,
)

class StatisticsViewModel(
    private val historyRepository: UsageHistoryRepository,
    private val usageTimeRepository: UsageTimeRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val usageDayProvider: UsageDayProvider = UsageDayProvider(),
    private val calculator: StatisticsCalculator = StatisticsCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    private var period = HistoryPeriod.SEVEN_DAYS
    private var loadJob: Job? = null

    init { load() }

    fun selectPeriod(period: HistoryPeriod) {
        if (this.period == period && loadJob?.isActive == true) return
        this.period = period
        load()
    }

    fun refresh() = load()

    private fun load() {
        loadJob?.cancel()
        val selectedPeriod = period
        val endDate = LocalDate.parse(usageDayProvider.currentUsageDay().key)
        val startDate = endDate.minusDays(selectedPeriod.days - 1L)
        val previousStartDate = startDate.minusDays(selectedPeriod.days)
        loadJob = viewModelScope.launch {
            _uiState.value = StatisticsUiState.Loading
            runCatching { withContext(Dispatchers.IO) { usageTimeRepository.getTodayUsage() } }
            combine(
                trackedAppRepository.observeTrackedPackageNames(),
                historyRepository.observeHistoryBetween(
                    previousStartDate.toString(),
                    endDate.toString(),
                ),
            ) { tracked, records -> tracked to records }
                .collect { (tracked, records) ->
                    _uiState.value = runCatching {
                        withContext(Dispatchers.IO) {
                            val analysis = calculator.calculateManagedApps(
                                records = records,
                                managedPackages = tracked,
                                currentEndDate = endDate,
                                days = selectedPeriod.days,
                            )
                            val apps = tracked.map { packageName ->
                                val installed = installedAppRepository.getUserInstalledApp(packageName)
                                StatisticsAppItem(
                                    packageName = packageName,
                                    appName = installed?.appName ?: packageName,
                                    appIcon = installed?.appIcon,
                                    usageSeconds = analysis.packageTotals[packageName] ?: 0L,
                                )
                            }.sortedByDescending(StatisticsAppItem::usageSeconds)
                            StatisticsUiState.Success(
                                period = selectedPeriod,
                                totalSeconds = analysis.totalUsageSeconds,
                                averageSeconds = analysis.averageUsageSeconds,
                                previousPeriodSeconds = analysis.previousPeriodUsageSeconds,
                                changePercent = analysis.changePercent,
                                topApp = apps.firstOrNull(),
                                dailyPoints = analysis.dailyPoints,
                                apps = apps,
                            )
                        }
                    }.getOrElse { StatisticsUiState.Error(it.message ?: "无法读取历史记录") }
                }
        }
    }

    class Factory(
        private val historyRepository: UsageHistoryRepository,
        private val usageTimeRepository: UsageTimeRepository,
        private val trackedAppRepository: TrackedAppRepository,
        private val installedAppRepository: InstalledAppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                return StatisticsViewModel(
                    historyRepository,
                    usageTimeRepository,
                    trackedAppRepository,
                    installedAppRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
