package com.example.timemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val usageTimeRepository: UsageTimeRepository,
    private val limitRepository: LimitRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun loadTodayUsage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val appUsages = runCatching {
                withContext(Dispatchers.IO) {
                    usageTimeRepository.getTodayUsage()
                }
            }.getOrElse { error ->
                _uiState.value = HomeUiState.Error(
                    error.message ?: "无法读取今日使用时间",
                )
                return@launch
            }

            limitRepository.observeAllLimits()
                .catch { error ->
                    _uiState.value = HomeUiState.Error(
                        error.message ?: "无法读取应用额度",
                    )
                }
                .collect { appLimits ->
                    val limitsByPackage = appLimits.associateBy(AppLimit::packageName)
                    val usageWithLimits = appUsages.map { appUsage ->
                        AppUsageWithLimit(
                            packageName = appUsage.packageName,
                            appName = appUsage.appName,
                            appIcon = appUsage.appIcon,
                            usedMinutes = appUsage.usageTimeMillis / MILLIS_PER_MINUTE,
                            limitMinutes = limitsByPackage[appUsage.packageName]?.limitMinutes,
                        )
                    }.sortedByDescending(AppUsageWithLimit::usedMinutes)

                    _uiState.value = HomeUiState.Success(usageWithLimits)
                }
        }
    }

    class Factory(
        private val usageTimeRepository: UsageTimeRepository,
        private val limitRepository: LimitRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(usageTimeRepository, limitRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
