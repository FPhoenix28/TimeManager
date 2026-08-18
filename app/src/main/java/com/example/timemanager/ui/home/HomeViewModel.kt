package com.example.timemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val usageRepository: UsageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadTodayUsage() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            _uiState.value = runCatching {
                withContext(Dispatchers.IO) {
                    usageRepository.getTodayUsage()
                }
            }.fold(
                onSuccess = { appUsages -> HomeUiState.Success(appUsages) },
                onFailure = { error ->
                    HomeUiState.Error(error.message ?: "无法读取今日使用时间")
                },
            )
        }
    }

    class Factory(
        private val usageRepository: UsageRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(usageRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
