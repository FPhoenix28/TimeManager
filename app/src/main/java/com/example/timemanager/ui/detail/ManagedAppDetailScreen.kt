package com.example.timemanager.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.components.UsageProgressIndicator
import com.example.timemanager.ui.limit.LimitEditorDialog
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun ManagedAppDetailScreen(
    viewModel: ManagedAppDetailViewModel,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    onOpenHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    when (val current = state) {
        ManagedAppDetailUiState.Loading -> FullScreenLoading(modifier)

        ManagedAppDetailUiState.Removed -> LaunchedEffect(Unit) { onRemoved() }

        is ManagedAppDetailUiState.Error -> ErrorContent(
            message = current.message,
            onRetry = viewModel::refresh,
            onBack = onBack,
            modifier = modifier,
        )

        is ManagedAppDetailUiState.Ready -> {
            DetailContent(
                state = current,
                onBack = onBack,
                onEditLimit = viewModel::beginLimitEdit,
                onEditSessionReminder = viewModel::beginSessionReminderEdit,
                onRemove = viewModel::removeManagedApp,
                onOpenHistory = { onOpenHistory(current.packageName) },
                modifier = modifier,
            )
            current.editor?.let { editor ->
                LimitEditorDialog(
                    editor = editor,
                    onInputChange = viewModel::updateLimitInput,
                    onDismiss = viewModel::dismissEditor,
                    onSave = viewModel::saveLimit,
                )
            }
            current.sessionEditor?.let { editor ->
                SessionReminderEditorDialog(
                    editor = editor,
                    onInputChange = viewModel::updateSessionReminderInput,
                    onPreset = { viewModel.saveSessionReminder(it) },
                    onSaveCustom = { viewModel.saveSessionReminder() },
                    onDismiss = viewModel::dismissSessionReminderEditor,
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: ManagedAppDetailUiState.Ready,
    onBack: () -> Unit,
    onEditLimit: () -> Unit,
    onEditSessionReminder: () -> Unit,
    onRemove: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier,
) {
    var confirmRemoval by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TimeManagerSpacing.medium,
            top = TimeManagerSpacing.small,
            end = TimeManagerSpacing.medium,
            bottom = TimeManagerSpacing.extraLarge,
        ),
    ) {
        item("topBar") {
            DetailTopBar(onBack = onBack)
        }
        item("identity") {
            AppIdentity(state)
        }
        item("usage") {
            TodayUsageSection(state)
        }
        item("settingsTitle") {
            SectionTitle("使用设置")
        }
        item("dailyLimit") {
            DetailSettingRow(
                label = "每日使用额度",
                value = state.baseLimitSeconds?.let(::formatDuration) ?: "未设置",
                onClick = onEditLimit,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item("sessionReminder") {
            DetailSettingRow(
                label = "连续使用提醒",
                value = "${state.sessionReminderMinutes} 分钟",
                onClick = onEditSessionReminder,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item("intervention") {
            DetailSettingRow(label = "超额冷静提醒", value = "已开启")
        }
        item("todayTitle") {
            SectionTitle("今天")
        }
        item("extension") {
            DetailSettingRow(
                label = "临时延长",
                value = formatSignedDuration(state.extensionSeconds),
            )
        }
        item("trendTitle") {
            SectionTitle("使用趋势")
        }
        item("history") {
            DetailSettingRow(
                label = "过去7天与30天",
                value = "查看历史",
                onClick = onOpenHistory,
            )
        }
        state.errorMessage?.let { message ->
            item("error") {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = TimeManagerSpacing.medium),
                )
            }
        }
        item("remove") {
            TextButton(
                onClick = { confirmRemoval = true },
                enabled = !state.isRemoving,
                modifier = Modifier.padding(top = TimeManagerSpacing.large),
            ) {
                Text(
                    text = if (state.isRemoving) "移除中" else "从管理列表移除",
                    color = if (state.isRemoving) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }

    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { if (!state.isRemoving) confirmRemoval = false },
            title = { Text("移除 ${state.appName}？") },
            text = {
                Text("移除${state.appName}后，TimeManager 将停止对它进行实时计时、额度提醒和超额干预。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        onRemove()
                    },
                ) {
                    Text("确认移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TimeManagerSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回",
            )
        }
        Text("应用详情", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AppIdentity(state: ManagedAppDetailUiState.Ready) {
    Row(
        modifier = Modifier.padding(top = TimeManagerSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            bitmap = state.appIcon,
            appName = state.appName,
            size = TimeManagerSpacing.extraLarge + TimeManagerSpacing.extraLarge,
        )
        Spacer(Modifier.size(TimeManagerSpacing.medium))
        Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall)) {
            Text(state.appName, style = MaterialTheme.typography.headlineSmall)
            Text(
                state.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.isInstalled) {
                Text(
                    "应用可能已卸载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TodayUsageSection(state: ManagedAppDetailUiState.Ready) {
    val effective = state.effectiveLimitSeconds
    val progress = when {
        effective == null -> 0f
        effective <= 0L && state.todayUsageSeconds > 0L -> 1f
        effective <= 0L -> 0f
        else -> state.todayUsageSeconds.toFloat() / effective.toFloat()
    }

    Column(
        modifier = Modifier.padding(top = TimeManagerSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
    ) {
        Text(
            text = "今日使用",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(state.todayUsageSeconds),
            style = MaterialTheme.typography.displaySmall,
        )
        if (effective != null) {
            Text(
                text = "${formatDuration(state.todayUsageSeconds)} / ${formatDuration(effective)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UsageProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (state.isOverLimit) {
                    "已超出 ${formatDuration(state.exceededSeconds)}"
                } else {
                    "剩余 ${formatDuration(state.remainingSeconds)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isOverLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.large)) {
                UsageMetric("基础额度", state.baseLimitSeconds?.let(::formatDuration) ?: "未设置")
                UsageMetric("今日延长", formatSignedDuration(state.extensionSeconds))
                UsageMetric("有效额度", formatDuration(effective))
            }
        }
    }
}

@Composable
private fun UsageMetric(label: String, value: String) {
    Column(modifier = Modifier.padding(top = TimeManagerSpacing.small)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(
            top = TimeManagerSpacing.extraLarge,
            bottom = TimeManagerSpacing.small,
        ),
    )
}

@Composable
private fun DetailSettingRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = TimeManagerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (onClick == null) value else "$value  ›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionReminderEditorDialog(
    editor: SessionReminderEditorUiState,
    onInputChange: (String) -> Unit,
    onPreset: (Int) -> Unit,
    onSaveCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连续使用提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact)) {
                Text("选择提醒时间，或输入5到180分钟。")
                Text(
                    text = "首次提醒后，将在连续使用8分钟、8分钟，之后每5分钟再次提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
                ) {
                    listOf(10, 15, 20).forEach { minutes ->
                        OutlinedButton(
                            onClick = { onPreset(minutes) },
                            enabled = !editor.isSaving,
                            modifier = Modifier.weight(1f),
                        ) { Text("$minutes") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
                ) {
                    listOf(30, 45, 60).forEach { minutes ->
                        OutlinedButton(
                            onClick = { onPreset(minutes) },
                            enabled = !editor.isSaving,
                            modifier = Modifier.weight(1f),
                        ) { Text("$minutes 分钟") }
                    }
                }
                OutlinedTextField(
                    value = editor.input,
                    onValueChange = onInputChange,
                    label = { Text("自定义") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = editor.errorMessage != null,
                    supportingText = editor.errorMessage?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveCustom,
                enabled = editor.input.isNotBlank() && !editor.isSaving,
            ) { Text(if (editor.isSaving) "保存中" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text("取消") }
        },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    FullScreenError(
        title = "无法加载应用详情",
        message = message,
        onRetry = onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60L -> "$seconds 秒"
    seconds % 60L == 0L -> "${seconds / 60L} 分钟"
    else -> "${seconds / 60L}分${seconds % 60L}秒"
}

private fun formatSignedDuration(seconds: Long): String =
    if (seconds <= 0L) "0 分钟" else "+${formatDuration(seconds)}"
