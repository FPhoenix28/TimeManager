package com.example.timemanager.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.ui.theme.TimeManagerSpacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeepFocusActiveScreen(
    viewModel: DeepFocusViewModel,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onEnded: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.focus.mode == FocusModeType.DEEP_FOCUS) { }
    DeepFocusContent(
        focus = state.focus,
        accessibilityEnabled = accessibilityEnabled,
        overlayEnabled = overlayEnabled,
        onEndConfirmed = { viewModel.endDeepFocus(onEnded) },
        onCompleted = { viewModel.endDeepFocus(onEnded) },
        onRequestAccessibility = onRequestAccessibility,
        onRequestOverlay = onRequestOverlay,
        modifier = modifier,
    )
}

@Composable
fun DeepFocusContent(
    focus: FocusModeState,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onEndConfirmed: () -> Unit,
    onCompleted: () -> Unit,
    onRequestAccessibility: (() -> Unit)? = null,
    onRequestOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showEndConfirmation by remember { mutableStateOf(false) }
    val completed = focus.isDeepFocusCompleted()
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TimeManagerSpacing.extraLarge),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (completed) "静心结束" else "静心",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(TimeManagerSpacing.extraLarge))
                if (completed) {
                    Text(
                        formatDeepFocusDuration(focus.endTimeMillis - focus.startTimeMillis),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(Modifier.height(TimeManagerSpacing.large))
                    Text("这段时间结束了", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(TimeManagerSpacing.extraLarge))
                    Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                        Text("完成")
                    }
                } else if (focus.isDeepFocusActive()) {
                    FocusCountdown(focus.endTimeMillis, MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(TimeManagerSpacing.large))
                    Text(
                        "现在不需要做任何事",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(TimeManagerSpacing.extraLarge))
                    Text(
                        "本次静心  ${formatDeepFocusDuration(focus.endTimeMillis - focus.startTimeMillis)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "结束时间  ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(focus.endTimeMillis))}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!accessibilityEnabled) {
                        Spacer(Modifier.height(TimeManagerSpacing.large))
                        Text(
                            "无障碍服务已关闭，当前无法阻止其它应用。",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        onRequestAccessibility?.let { callback ->
                            TextButton(onClick = callback) { Text("重新开启") }
                        }
                    }
                    if (!overlayEnabled) {
                        Spacer(Modifier.height(TimeManagerSpacing.small))
                        Text(
                            "悬浮窗权限已关闭，当前无法阻止其它应用；静心界面仍可在 TimeManager 内查看。",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        onRequestOverlay?.let { callback ->
                            TextButton(onClick = callback) { Text("恢复悬浮窗权限") }
                        }
                    }
                    Spacer(Modifier.height(TimeManagerSpacing.extraLarge))
                    HoldToEndButton(onCompleted = { showEndConfirmation = true })
                } else {
                    Text("静心状态已解除", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(TimeManagerSpacing.large))
                    Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                        Text("返回首页")
                    }
                }
            }
        }

        if (showEndConfirmation) {
            val remainingMinutes = (focus.remainingMillis() + 59_999L) / 60_000L
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TimeManagerSpacing.extraLarge)
                        .widthIn(max = 360.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                ) {
                    Column(modifier = Modifier.padding(TimeManagerSpacing.large)) {
                        Text("确定提前结束吗？", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(TimeManagerSpacing.medium))
                        Text(
                            "静心还有约 $remainingMinutes 分钟。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(TimeManagerSpacing.large))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = {
                                showEndConfirmation = false
                                onEndConfirmed()
                            }) { Text("结束静心") }
                            Button(onClick = { showEndConfirmation = false }) {
                                Text("继续静心")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldToEndButton(onCompleted: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val latestOnCompleted by rememberUpdatedState(onCompleted)
    Box(
        modifier = Modifier
            .size(132.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            progress.snapTo(0f)
                            var completed = false
                            val animation = launch {
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 3_000,
                                        easing = LinearEasing,
                                    ),
                                )
                                completed = true
                                latestOnCompleted()
                            }
                            tryAwaitRelease()
                            if (!completed) animation.cancel()
                            progress.snapTo(0f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxSize(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Text("长按 3 秒\n提前结束", textAlign = TextAlign.Center)
            }
        }
    }
}

private fun formatDeepFocusDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis.coerceAtLeast(0L) + 59_999L) / 60_000L
    return if (totalMinutes >= 60L && totalMinutes % 60L == 0L) {
        "${totalMinutes / 60L} 小时"
    } else {
        "$totalMinutes 分钟"
    }
}
