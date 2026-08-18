package com.example.timemanager.ui.limit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.LimitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LimitViewModel(
    private val installedAppRepository: InstalledAppRepository,
    private val limitRepository: LimitRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LimitUiState>(LimitUiState.Loading)
    val uiState: StateFlow<LimitUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = LimitUiState.Loading
            _uiState.value = runCatching {
                withContext(Dispatchers.IO) {
                    val installedApps = installedAppRepository.getUserInstalledApps()
                    val limitsByPackage = limitRepository.observeAllLimits()
                        .first()
                        .associateBy(AppLimit::packageName)

                    LimitUiState.Success(
                        apps = installedApps.map { installedApp ->
                            LimitAppItemUiState(
                                packageName = installedApp.packageName,
                                appName = installedApp.appName,
                                appIcon = installedApp.appIcon,
                                limitInput = limitsByPackage[installedApp.packageName]
                                    ?.limitMinutes
                                    ?.toString()
                                    .orEmpty(),
                            )
                        },
                    )
                }
            }.getOrElse { error ->
                LimitUiState.Error(error.message ?: "无法加载应用额度")
            }
        }
    }

    fun updateLimitInput(packageName: String, input: String) {
        if (input.length > MAX_LIMIT_INPUT_LENGTH || input.any { !it.isDigit() }) {
            return
        }
        updateApp(packageName) { app ->
            app.copy(
                limitInput = input,
                isSaved = false,
                errorMessage = null,
            )
        }
    }

    fun saveLimit(packageName: String) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        val app = state.apps.firstOrNull { it.packageName == packageName } ?: return
        val limitMinutes = app.limitInput.toIntOrNull()
        if (limitMinutes == null) {
            updateApp(packageName) {
                it.copy(errorMessage = "请输入有效分钟数")
            }
            return
        }

        updateApp(packageName) {
            it.copy(isSaving = true, isSaved = false, errorMessage = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    limitRepository.saveLimit(
                        AppLimit(
                            packageName = packageName,
                            limitMinutes = limitMinutes,
                        ),
                    )
                }
            }.fold(
                onSuccess = {
                    updateApp(packageName) {
                        it.copy(isSaving = false, isSaved = true)
                    }
                },
                onFailure = { error ->
                    updateApp(packageName) {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "保存失败",
                        )
                    }
                },
            )
        }
    }

    private fun updateApp(
        packageName: String,
        transform: (LimitAppItemUiState) -> LimitAppItemUiState,
    ) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        _uiState.value = state.copy(
            apps = state.apps.map { app ->
                if (app.packageName == packageName) transform(app) else app
            },
        )
    }

    class Factory(
        private val installedAppRepository: InstalledAppRepository,
        private val limitRepository: LimitRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LimitViewModel::class.java)) {
                return LimitViewModel(installedAppRepository, limitRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val MAX_LIMIT_INPUT_LENGTH = 4
    }
}
