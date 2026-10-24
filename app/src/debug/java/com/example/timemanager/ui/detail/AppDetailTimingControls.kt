package com.example.timemanager.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.timemanager.config.*
import com.example.timemanager.ui.theme.TimeManagerSpacing
import kotlinx.coroutines.launch

fun formatAppDetailDuration(seconds: Long): String = formatPreciseDuration(seconds)

/** Only configuration is exposed. These controls have no runtime state references. */
@Composable
fun AppDetailTimingControls() {
    val controller = BuildTimeConfig.controller
    val timing by controller.settings.collectAsState()
    var editing by rememberSaveable { mutableStateOf<DebugTimingParameter?>(null) }
    var more by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
        Text("测试版高级参数", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = TimeManagerSpacing.large))
        Text("仅 TimeManager Dev · 全局参数，影响所有受管理应用。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DebugTimingParameter.entries.filter {
            more || it !in listOf(DebugTimingParameter.NOTIFICATION_FIRST, DebugTimingParameter.NOTIFICATION_SECOND)
        }.forEach { parameter ->
            TextButton(onClick = { editing = parameter }, modifier = Modifier.fillMaxWidth()) {
                Text(parameter.label, modifier = Modifier.weight(1f))
                Text("${formatPreciseDuration(parameter.seconds(timing))}  ›")
            }
        }
        Text("修改进阶时间不改写已确定的下一提醒点；后续提醒推进时使用新参数。已开始的冷却倒计时保持不变。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { more = !more }) { Text(if (more) "收起更多测试参数" else "更多测试参数（通知）") }
        TextButton(onClick = controller::reset) { Text("恢复正式版默认参数") }
        Text("仅恢复以上全局参数，不修改每日额度、首次提醒或当前计时状态。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { parameter ->
        AppDetailDurationDialog(parameter.label, parameter.seconds(timing),
            onSave = { seconds ->
                runCatching { controller.save(parameter, seconds) }.exceptionOrNull()?.let {
                    if (parameter == DebugTimingParameter.NOTIFICATION_FIRST || parameter == DebugTimingParameter.NOTIFICATION_SECOND)
                        "通知2必须晚于通知1，且时间必须为正数" else it.message ?: "保存失败"
                }
            }, onDismiss = { editing = null })
    }
}

@Composable
fun AppDetailDurationDialog(
    title: String,
    initialSeconds: Long,
    onSave: suspend (Long) -> String?,
    onDismiss: () -> Unit,
) {
    val initial = remember { DurationInput.fromSeconds(initialSeconds) }
    var hours by rememberSaveable { mutableStateOf(initial.hours) }
    var minutes by rememberSaveable { mutableStateOf(initial.minutes) }
    var seconds by rememberSaveable { mutableStateOf(initial.seconds) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                DurationEditor(hours, minutes, seconds, !saving,
                    { hours = it; error = null }, { minutes = it; error = null }, { seconds = it; error = null })
                Text("至少1秒；小时0–99，分钟和秒0–59。点击保存后生效。",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val total = runCatching { DurationInput(hours, minutes, seconds).totalSeconds() }
                    .getOrElse { error = it.message; return@TextButton }
                saving = true
                scope.launch {
                    error = onSave(total)
                    saving = false
                    if (error == null) onDismiss()
                }
            }) { Text(if (saving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}

@Composable
private fun DurationEditor(
    hours: String, minutes: String, seconds: String, enabled: Boolean,
    onHours: (String) -> Unit, onMinutes: (String) -> Unit, onSeconds: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
        listOf(Triple("小时", hours, onHours), Triple("分钟", minutes, onMinutes), Triple("秒", seconds, onSeconds))
            .forEach { (label, value, change) ->
                OutlinedTextField(value, { if (it.length <= 3) change(it) },
                    label = { Text(label) }, enabled = enabled, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))
            }
    }
}
