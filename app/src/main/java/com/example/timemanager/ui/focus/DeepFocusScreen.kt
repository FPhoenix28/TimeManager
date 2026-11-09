package com.example.timemanager.ui.focus

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.domain.todo.TodoDayState
import com.example.timemanager.ui.components.CalmPanel
import com.example.timemanager.ui.components.CalmPanelTone
import com.example.timemanager.ui.intervention.ResponsiveInterventionScaffold
import com.example.timemanager.ui.theme.TimeManagerSpacing
import java.util.Date
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

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
    DeepFocusTodoContent(
        focus = state.focus,
        todoDayState = viewModel.todoDayState,
        accessibilityEnabled = accessibilityEnabled,
        overlayEnabled = overlayEnabled,
        onEndConfirmed = { viewModel.endDeepFocus(onEnded) },
        onCompleted = { viewModel.endDeepFocus(onEnded) },
        onRequestAccessibility = onRequestAccessibility,
        onRequestOverlay = onRequestOverlay,
        modifier = modifier,
    )
}

/** Shared Activity/overlay binding to the cached, complete day-state flow. */
@Composable
internal fun DeepFocusTodoContent(
    focus: FocusModeState,
    todoDayState: StateFlow<TodoDayState>,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onEndConfirmed: () -> Unit,
    onCompleted: () -> Unit,
    onRequestAccessibility: (() -> Unit)? = null,
    onRequestOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dayState by todoDayState.collectAsStateWithLifecycle()
    val unfinished = remember(dayState) { dayState.unfinished }
    DeepFocusContent(focus, accessibilityEnabled, overlayEnabled, onEndConfirmed, onCompleted,
        onRequestAccessibility, onRequestOverlay, modifier, unfinished)
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
    unfinishedTodos: List<TodoEntity> = emptyList(),
) {
    var showEndConfirmation by remember { mutableStateOf(false) }
    val completed = focus.isDeepFocusCompleted()
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = TimeManagerSpacing.extraLarge, vertical = TimeManagerSpacing.large),
            ) {
                if (focus.isDeepFocusActive()) {
                    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                    val maxListHeight = (maxHeight * if (wide) 0.45f else 0.28f).coerceAtMost(260.dp)
                    val compactHeight = maxHeight < 500.dp
                    val information: @Composable () -> Unit = {
                        DeepFocusInformation(focus, unfinishedTodos, maxListHeight, compactHeight,
                            accessibilityEnabled, overlayEnabled, onRequestAccessibility, onRequestOverlay)
                    }
                    if (wide) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraLarge)) {
                            Box(Modifier.weight(1f)) { information() }
                            HoldToEndButton(onCompleted = { showEndConfirmation = true })
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { information() }
                            Spacer(Modifier.height(if (compactHeight) TimeManagerSpacing.compact else TimeManagerSpacing.large))
                            HoldToEndButton(onCompleted = { showEndConfirmation = true })
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
                        } else {
                            Text("静心状态已解除", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(TimeManagerSpacing.large))
                            Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                                Text("返回首页")
                            }
                        }
                    }
                }
            }
        }

        if (showEndConfirmation) {
            val remainingMinutes = (focus.remainingMillis() + 59_999L) / 60_000L
            ResponsiveInterventionScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                scrimAlpha = 0.42f,
                portraitMaxWidth = 360.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = { Text("确定提前结束吗？", style = MaterialTheme.typography.headlineSmall) },
                mainContent = {
                    Text("静心还有约 $remainingMinutes 分钟。", style = MaterialTheme.typography.bodyMedium)
                },
                actions = {
                    Button(
                        onClick = { showEndConfirmation = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("继续静心") }
                    TextButton(
                        onClick = {
                            showEndConfirmation = false
                            onEndConfirmed()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("结束静心") }
                },
            )
        }
    }
}

@Composable
private fun DeepFocusInformation(
    focus: FocusModeState,
    todos: List<TodoEntity>,
    maxListHeight: Dp,
    compactHeight: Boolean,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onRequestAccessibility: (() -> Unit)?,
    onRequestOverlay: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("静心模式", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(if (compactHeight) TimeManagerSpacing.extraSmall else TimeManagerSpacing.medium))
        FocusCountdown(
            focus.endTimeMillis,
            (if (compactHeight) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge)
                .copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(if (compactHeight) TimeManagerSpacing.small else TimeManagerSpacing.large))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
        ) {
            Text(if (todos.isEmpty()) "现在不需要做任何事" else "暂时放下手机，专注眼前的事情",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (todos.isNotEmpty()) DeepFocusTodoCard(todos, maxListHeight)
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall)) {
                Text("本次静心  ${formatDeepFocusDuration(focus.endTimeMillis - focus.startTimeMillis)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("结束时间  ${DateFormat.getTimeFormat(LocalContext.current).format(Date(focus.endTimeMillis))}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!accessibilityEnabled || !overlayEnabled) {
                CalmPanel(CalmPanelTone.IMMERSIVE) {
                    if (!accessibilityEnabled) {
                        Text("开启无障碍服务后，静心期间才能暂时限制其他应用。",
                            style = MaterialTheme.typography.bodyMedium)
                        onRequestAccessibility?.let { callback -> TextButton(onClick = callback) { Text("重新开启") } }
                    }
                    if (!overlayEnabled) {
                        Text("允许显示在其他应用上层后，静心才能在其他应用上显示并限制使用。",
                            style = MaterialTheme.typography.bodyMedium)
                        onRequestOverlay?.let { callback -> TextButton(onClick = callback) { Text("恢复悬浮窗权限") } }
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
    val controlSize = 132.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Box(
        modifier = Modifier
            .size(controlSize)
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
            strokeWidth = 3.dp,
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(modifier = Modifier.size(controlSize - 20.dp), contentAlignment = Alignment.Center) {
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
