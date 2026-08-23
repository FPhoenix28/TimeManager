package com.example.timemanager.ui.statistics

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.repository.InstalledAppRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppHistoryUiState {
    data object Loading : AppHistoryUiState
    data class Error(val message: String) : AppHistoryUiState
    data class Success(
        val packageName: String,
        val appName: String,
        val appIcon: Bitmap?,
        val period: HistoryPeriod,
        val totalSeconds: Long,
        val previousPeriodSeconds: Long,
        val changePercent: Double?,
        val dailyPoints: List<DailyUsagePoint>,
        val days: List<HistoryDayItem>,
    ) : AppHistoryUiState
}

data class HistoryDayItem(
    val usageDate: String,
    val label: String,
    val usageSeconds: Long,
)

class AppHistoryViewModel(
    private val historyRepository: UsageHistoryRepository,
    private val usageTimeRepository: UsageTimeRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val usageDayProvider: UsageDayProvider = UsageDayProvider(),
    private val calculator: StatisticsCalculator = StatisticsCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppHistoryUiState>(AppHistoryUiState.Loading)
    val uiState: StateFlow<AppHistoryUiState> = _uiState.asStateFlow()
    private var packageName: String? = null
    private var period = HistoryPeriod.SEVEN_DAYS
    private var loadJob: Job? = null

    fun open(packageName: String) {
        require(packageName.isNotBlank())
        this.packageName = packageName
        load()
    }

    fun selectPeriod(period: HistoryPeriod) {
        if (this.period == period && loadJob?.isActive == true) return
        this.period = period
        load()
    }

    fun refresh() = load()

    private fun load() {
        val selectedPackage = packageName ?: return
        val selectedPeriod = period
        val endDate = LocalDate.parse(usageDayProvider.currentUsageDay().key)
        val startDate = endDate.minusDays(selectedPeriod.days - 1L)
        val previousStartDate = startDate.minusDays(selectedPeriod.days)
        val currentState = _uiState.value
        val hasMatchingContent = currentState is AppHistoryUiState.Success &&
            currentState.packageName == selectedPackage
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val totalStartedAt = SystemClock.elapsedRealtime()
            if (!hasMatchingContent) _uiState.value = AppHistoryUiState.Loading
            val usageStartedAt = SystemClock.elapsedRealtime()
            runCatching {
                withContext(Dispatchers.IO) { usageTimeRepository.getTodayUsage(selectedPackage) }
            }
            val usageDuration = SystemClock.elapsedRealtime() - usageStartedAt
            historyRepository.observeHistoryBetween(
                previousStartDate.toString(),
                endDate.toString(),
            ).collect { records ->
                val calculationStartedAt = SystemClock.elapsedRealtime()
                _uiState.value = runCatching {
                    withContext(Dispatchers.IO) {
                        val app = installedAppRepository.getUserInstalledApp(selectedPackage)
                        val analysis = calculator.calculateApp(
                            records = records,
                            packageName = selectedPackage,
                            currentEndDate = endDate,
                            days = selectedPeriod.days,
                        )
                        val days = analysis.dailyPoints.asReversed().mapIndexed { offset, point ->
                            val date = LocalDate.parse(point.date)
                            HistoryDayItem(
                                usageDate = date.toString(),
                                label = when (offset) {
                                    0 -> "今天"
                                    1 -> "昨天"
                                    2 -> "前天"
                                    else -> date.toString()
                                },
                                usageSeconds = point.usageSeconds,
                            )
                        }
                        AppHistoryUiState.Success(
                            packageName = selectedPackage,
                            appName = app?.appName ?: selectedPackage,
                            appIcon = app?.appIcon,
                            period = selectedPeriod,
                            totalSeconds = analysis.totalUsageSeconds,
                            previousPeriodSeconds = analysis.previousPeriodUsageSeconds,
                            changePercent = analysis.changePercent,
                            dailyPoints = analysis.dailyPoints,
                            days = days,
                        )
                    }
                }.getOrElse {
                    if (hasMatchingContent) _uiState.value else {
                        AppHistoryUiState.Error(it.message ?: "无法读取应用历史")
                    }
                }
                Log.d(
                    TAG,
                    "packageName=$selectedPackage UsageStats=${usageDuration}ms " +
                        "RoomAndCalculation=${SystemClock.elapsedRealtime() - calculationStartedAt}ms " +
                        "Total=${SystemClock.elapsedRealtime() - totalStartedAt}ms",
                )
            }
        }
    }

    class Factory(
        private val historyRepository: UsageHistoryRepository,
        private val usageTimeRepository: UsageTimeRepository,
        private val installedAppRepository: InstalledAppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppHistoryViewModel::class.java)) {
                return AppHistoryViewModel(
                    historyRepository,
                    usageTimeRepository,
                    installedAppRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val TAG = "HistoryLoad"
    }
}
