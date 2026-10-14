package com.example.timemanager.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.ui.theme.TimeManagerSpacing
import java.text.DateFormat
import java.util.Date

@Composable
fun DeepFocusSetupScreen(
    viewModel: DeepFocusViewModel,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onCheckAccessibility: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStartConfirmation by remember { mutableStateOf(false) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TimeManagerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.large),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text("静心模式", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "在设定时间内暂时离开其它应用。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("duration") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                Text("静心时长", style = MaterialTheme.typography.titleMedium)
                listOf(listOf(15, 25, 30), listOf(45, 60, 90)).forEach { values ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                        values.forEach { minutes ->
                            val onClick = { viewModel.selectPreset(minutes) }
                            if (state.durationMinutes == minutes && state.customDurationInput.isEmpty()) {
                                Button(onClick = onClick) { Text("$minutes 分钟") }
                            } else {
                                OutlinedButton(onClick = onClick) { Text("$minutes 分钟") }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.customDurationInput,
                    onValueChange = viewModel::updateCustomDuration,
                    label = { Text("自定义分钟数（1–720）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!accessibilityEnabled || !overlayEnabled || state.error != null) {
            item("status") {
                Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                    if (!accessibilityEnabled) {
                        Text(
                            "静心模式需要开启无障碍服务才能暂时阻止其它应用。",
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onRequestAccessibility) { Text("前往开启") }
                    }
                    if (!overlayEnabled) {
                        Text(
                            "悬浮窗权限未开启，将只能在 TimeManager 页面内显示静心界面。",
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onRequestOverlay) { Text("开启悬浮窗权限") }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item("start") {
            Button(
                onClick = {
                    if (onCheckAccessibility()) showStartConfirmation = true
                    else showPermissionExplanation = true
                },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始静心") }
        }
    }

    if (showStartConfirmation) {
        val duration = state.durationMinutes ?: 0
        val endTime = System.currentTimeMillis() + duration * 60_000L
        val replacesFocus = state.focus.isActive()
        AlertDialog(
            onDismissRequest = { showStartConfirmation = false },
            title = { Text(if (replacesFocus) "开始静心并结束当前专注？" else "开始静心？") },
            text = {
                Text(
                    buildString {
                        if (replacesFocus) append("开始静心将结束当前专注模式。\n\n")
                        append("静心开始后，除必要系统功能外，你将暂时无法使用其它应用。\n\n")
                        append("持续时间：$duration 分钟\n")
                        append("结束时间：${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endTime))}")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirmation = false
                    if (onCheckAccessibility()) viewModel.startDeepFocus(replacesFocus, onStarted)
                    else showPermissionExplanation = true
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirmation = false }) { Text("取消") }
            },
        )
    }
    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("启用静心干预") },
            text = { Text("静心模式需要开启无障碍服务才能暂时阻止其它应用。开启后再开始静心。") },
            confirmButton = {
                TextButton(onClick = { showPermissionExplanation = false; onRequestAccessibility() }) {
                    Text("前往开启")
                }
            },
            dismissButton = { TextButton(onClick = { showPermissionExplanation = false }) { Text("取消") } },
        )
    }
}
