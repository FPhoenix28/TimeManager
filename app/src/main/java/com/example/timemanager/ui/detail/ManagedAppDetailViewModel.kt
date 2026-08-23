package com.example.timemanager.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.ManagedAppRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.data.repository.SessionSettingsRepository
import com.example.timemanager.data.model.AppSessionSettings
import com.example.timemanager.ui.limit.LimitEditorUiState
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagedAppDetailViewModel(
    private val installedAppRepository: InstalledAppRepository,
    private val usageTimeRepository: UsageTimeRepository,
    private val limitRepository: LimitRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val interventionRepository: InterventionRepository,
    private val managedAppRepository: ManagedAppRepository,
    private val sessionSettingsRepository: SessionSettingsRepository,
    private val usageDayProvider: UsageDayProvider = UsageDayProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<ManagedAppDetailUiState>(
        ManagedAppDetailUiState.Loading,
    )
    val uiState: StateFlow<ManagedAppDetailUiState> = _uiState.asStateFlow()
    private var packageName: String? = null
    private var loadJob: Job? = null

    fun open(packageName: String) {
        require(packageName.isNotBlank())
        this.packageName = packageName
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ManagedAppDetailUiState.Loading
            combine(
                trackedAppRepository.observeTrackedPackageNames(),
                limitRepository.observeLimit(packageName),
                sessionSettingsRepository.observe(packageName),
            ) { trackedPackages: Set<String>, limit: AppLimit?, session: AppSessionSettings ->
                DetailSources(packageName in trackedPackages, limit, session.reminderMinutes)
            }.collect { result: DetailSources ->
                    val isTracked = result.isTracked
                    val limit = result.limit
                    if (!isTracked) {
                        _uiState.value = ManagedAppDetailUiState.Removed
                        return@collect
                    }
                    _uiState.value = runCatching {
                        withContext(Dispatchers.IO) {
                            buildState(packageName, limit, result.sessionReminderMinutes)
                        }
                    }.getOrElse { error ->
                        ManagedAppDetailUiState.Error(
                            error.message ?: "无法加载应用详情",
                        )
                    }
                }
        }
    }

    fun refresh() {
        packageName?.let(::open)
    }

    fun beginLimitEdit() {
        updateReady { state ->
            state.copy(
                editor = LimitEditorUiState(
                    packageName = state.packageName,
                    appName = state.appName,
                    appIcon = state.appIcon,
                    limitInput = state.baseLimitSeconds
                        ?.div(SECONDS_PER_MINUTE)
                        ?.toString()
                        .orEmpty(),
                    isNew = false,
                ),
            )
        }
    }

    fun updateLimitInput(input: String) {
        if (input.length > MAX_LIMIT_INPUT_LENGTH || input.any { !it.isDigit() }) return
        updateReady { state ->
            state.copy(editor = state.editor?.copy(limitInput = input, errorMessage = null))
        }
    }

    fun dismissEditor() {
        updateReady { state ->
            if (state.editor?.isSaving == true) state else state.copy(editor = null)
        }
    }

    fun saveLimit() {
        val state = _uiState.value as? ManagedAppDetailUiState.Ready ?: return
        val editor = state.editor ?: return
        val minutes = editor.limitInput.toIntOrNull()
        if (minutes == null) {
            updateReady { it.copy(editor = editor.copy(errorMessage = "请输入有效分钟数")) }
            return
        }
        updateReady { it.copy(editor = editor.copy(isSaving = true, errorMessage = null)) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    check(trackedAppRepository.isTracked(editor.packageName)) {
                        "该应用已不在管理列表中"
                    }
                    limitRepository.saveLimit(AppLimit(editor.packageName, minutes))
                }
            }.onFailure { error ->
                updateReady {
                    it.copy(
                        editor = editor.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "保存失败",
                        ),
                    )
                }
            }
        }
    }

    fun beginSessionReminderEdit() {
        updateReady { state ->
            state.copy(
                sessionEditor = SessionReminderEditorUiState(
                    input = state.sessionReminderMinutes.toString(),
                ),
            )
        }
    }

    fun updateSessionReminderInput(input: String) {
        if (input.length > 3 || input.any { !it.isDigit() }) return
        updateReady { state ->
            state.copy(
                sessionEditor = state.sessionEditor?.copy(input = input, errorMessage = null),
            )
        }
    }

    fun dismissSessionReminderEditor() {
        updateReady { state ->
            if (state.sessionEditor?.isSaving == true) state else state.copy(sessionEditor = null)
        }
    }

    fun saveSessionReminder(presetMinutes: Int? = null) {
        val state = _uiState.value as? ManagedAppDetailUiState.Ready ?: return
        val editor = state.sessionEditor ?: return
        val minutes = presetMinutes ?: editor.input.toIntOrNull()
        if (minutes == null || minutes !in SessionSettingsRepository.MIN_REMINDER_MINUTES..
            SessionSettingsRepository.MAX_REMINDER_MINUTES
        ) {
            updateReady {
                it.copy(
                    sessionEditor = editor.copy(
                        errorMessage = "请输入5到180之间的分钟数",
                    ),
                )
            }
            return
        }
        updateReady {
            it.copy(sessionEditor = editor.copy(isSaving = true, errorMessage = null))
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    sessionSettingsRepository.save(state.packageName, minutes)
                }
            }.onFailure { error ->
                updateReady {
                    it.copy(
                        sessionEditor = editor.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "保存失败",
                        ),
                    )
                }
            }
        }
    }

    fun removeManagedApp() {
        val state = _uiState.value as? ManagedAppDetailUiState.Ready ?: return
        updateReady { it.copy(isRemoving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    managedAppRepository.remove(state.packageName)
                }
            }.onFailure { error ->
                updateReady {
                    it.copy(
                        isRemoving = false,
                        errorMessage = error.message ?: "移除管理应用失败",
                    )
                }
            }
        }
    }

    private suspend fun buildState(
        packageName: String,
        limit: AppLimit?,
        sessionReminderMinutes: Int,
    ): ManagedAppDetailUiState.Ready {
        val installedApp = installedAppRepository.getUserInstalledApp(packageName)
        val usageSeconds = usageTimeRepository.getTodayUsage(packageName)
        val extensionSeconds = interventionRepository.getState(
            packageName,
            usageDayProvider.currentUsageDay().key,
        ).todayExtensionSeconds
        val baseSeconds = limit?.limitMinutes?.times(SECONDS_PER_MINUTE)
        val effectiveSeconds = baseSeconds?.plus(extensionSeconds)
        return ManagedAppDetailUiState.Ready(
            packageName = packageName,
            appName = installedApp?.appName ?: packageName,
            appIcon = installedApp?.appIcon,
            isInstalled = installedApp != null,
            todayUsageSeconds = usageSeconds,
            baseLimitSeconds = baseSeconds,
            extensionSeconds = extensionSeconds,
            effectiveLimitSeconds = effectiveSeconds,
            remainingSeconds = effectiveSeconds
                ?.minus(usageSeconds)
                ?.coerceAtLeast(0L)
                ?: 0L,
            exceededSeconds = effectiveSeconds
                ?.let { (usageSeconds - it).coerceAtLeast(0L) }
                ?: 0L,
            sessionReminderMinutes = sessionReminderMinutes,
            isOverLimit = effectiveSeconds != null && usageSeconds >= effectiveSeconds,
        )
    }

    private fun updateReady(
        transform: (ManagedAppDetailUiState.Ready) -> ManagedAppDetailUiState.Ready,
    ) {
        val state = _uiState.value as? ManagedAppDetailUiState.Ready ?: return
        _uiState.value = transform(state)
    }

    class Factory(
        private val installedAppRepository: InstalledAppRepository,
        private val usageTimeRepository: UsageTimeRepository,
        private val limitRepository: LimitRepository,
        private val trackedAppRepository: TrackedAppRepository,
        private val interventionRepository: InterventionRepository,
        private val managedAppRepository: ManagedAppRepository,
        private val sessionSettingsRepository: SessionSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ManagedAppDetailViewModel::class.java)) {
                return ManagedAppDetailViewModel(
                    installedAppRepository,
                    usageTimeRepository,
                    limitRepository,
                    trackedAppRepository,
                    interventionRepository,
                    managedAppRepository,
                    sessionSettingsRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_LIMIT_INPUT_LENGTH = 4
    }

    private data class DetailSources(
        val isTracked: Boolean,
        val limit: AppLimit?,
        val sessionReminderMinutes: Int,
    )
}
