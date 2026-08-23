package com.example.timemanager.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.timemanager.ui.limit.LimitEditorDialog

@Composable
fun ManagedAppDetailScreen(
    viewModel: ManagedAppDetailViewModel,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    onOpenHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    when (val current = state) {
        ManagedAppDetailUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        ManagedAppDetailUiState.Removed -> LaunchedEffect(Unit) { onRemoved() }

        is ManagedAppDetailUiState.Error -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(current.message)
            Button(onClick = viewModel::refresh) { Text("重试") }
            TextButton(onClick = onBack) { Text("返回") }
        }

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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text("应用详情", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item(key = "identity") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailIcon(state.appIcon, state.appName)
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(state.appName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.isInstalled) {
                        Text("应用可能已卸载", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item(key = "usage") { UsageCard(state) }
        item(key = "management_title") {
            Text("使用管理", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "management") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(
                        "每日使用额度",
                        state.baseLimitSeconds?.let(::formatDuration) ?: "未设置",
                        onClick = onEditLimit,
                    )
                    HorizontalDivider()
                    SettingRow(
                        "连续使用提醒",
                        "${state.sessionReminderMinutes} 分钟",
                        onClick = onEditSessionReminder,
                    )
                    HorizontalDivider()
                    SettingRow("超额冷静页", "已开启")
                    HorizontalDivider()
                    SettingRow("今日临时延长", formatSignedDuration(state.extensionSeconds))
                    HorizontalDivider()
                    SettingRow("使用历史", "查看", onClick = onOpenHistory)
                }
            }
        }
        state.errorMessage?.let { message ->
            item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item(key = "remove") {
            OutlinedButton(
                onClick = { confirmRemoval = true },
                enabled = !state.isRemoving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRemoving) "移除中" else "从管理列表移除")
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
                Button(
                    onClick = {
                        confirmRemoval = false
                        onRemove()
                    },
                ) { Text("确认移除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text("取消") }
            },
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择提醒时间，或输入5到180分钟。")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15, 20, 30).forEach { minutes ->
                        OutlinedButton(
                            onClick = { onPreset(minutes) },
                            enabled = !editor.isSaving,
                            modifier = Modifier.weight(1f),
                        ) { Text("$minutes") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(45, 60).forEach { minutes ->
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
private fun UsageCard(state: ManagedAppDetailUiState.Ready) {
    val effective = state.effectiveLimitSeconds
    val progress = when {
        effective == null -> 0f
        effective <= 0L && state.todayUsageSeconds > 0L -> 1f
        effective <= 0L -> 0f
        else -> (state.todayUsageSeconds.toFloat() / effective).coerceIn(0f, 1f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("今日使用", style = MaterialTheme.typography.titleMedium)
            Text(
                if (effective == null) {
                    formatDuration(state.todayUsageSeconds)
                } else {
                    "${formatDuration(state.todayUsageSeconds)} / ${formatDuration(effective)}"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (effective != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    if (state.isOverLimit) {
                        "已超出 ${formatDuration(state.exceededSeconds)}"
                    } else {
                        "剩余 ${formatDuration(state.remainingSeconds)}"
                    },
                )
                Text("基础额度：${state.baseLimitSeconds?.let(::formatDuration) ?: "未设置"}")
                Text("今日延长：${formatSignedDuration(state.extensionSeconds)}")
                Text("当前有效额度：${formatDuration(effective)}")
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
            Text(if (onClick == null) value else "$value  >")
        }
    }
}

@Composable
private fun DetailIcon(bitmap: Bitmap?, appName: String) {
    val icon = remember(bitmap) { bitmap?.asImageBitmap() }
    if (icon == null) {
        Text(appName.firstOrNull()?.toString() ?: "?", style = MaterialTheme.typography.headlineLarge)
    } else {
        Image(
            bitmap = icon,
            contentDescription = "$appName 图标",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
        )
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60L -> "$seconds 秒"
    seconds % 60L == 0L -> "${seconds / 60L} 分钟"
    else -> "${seconds / 60L}分${seconds % 60L}秒"
}

private fun formatSignedDuration(seconds: Long): String =
    if (seconds <= 0L) "0 分钟" else "+${formatDuration(seconds)}"
