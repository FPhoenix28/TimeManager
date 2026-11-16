package com.example.timemanager.ui.intervention

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.domain.intervention.InterventionPresentation
import com.example.timemanager.domain.intervention.InterventionPresentationPolicy
import com.example.timemanager.ui.components.CalmPanelTone
import com.example.timemanager.ui.components.FeatureArtwork
import com.example.timemanager.ui.components.containerColor
import com.example.timemanager.ui.theme.TimeManagerSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** All admission/progression state lives outside Compose; layout changes only move content. */
@Composable
internal fun SessionFrictionContent(
    request: LimitInterventionRequest,
    onStayHome: () -> Unit,
    onContinue: () -> Unit,
    todoSummary: TodoSummary = TodoSummary(),
    onScheduleDeepFocus: (suspend () -> Unit)? = null,
) {
    val gate = request.sessionGate ?: return
    var confirming by remember(gate.id) { mutableStateOf(false) }
    var scheduling by remember(gate.id) { mutableStateOf(false) }
    var scheduleError by remember(gate.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val remaining by produceState(
        initialValue = gateRemainingSeconds(gate.endTimeMillis, SystemClock.elapsedRealtime()),
        key1 = gate.id,
        key2 = gate.endTimeMillis,
    ) {
        do {
            value = gateRemainingSeconds(gate.endTimeMillis, SystemClock.elapsedRealtime())
            if (value == 0L) break
            delay(100L)
        } while (true)
    }
    val reentry = gate.phase == SessionGatePhase.REENTRY
    val overlayChoice = request.presentation == InterventionPresentation.OVERLAY_IN_PLACE
    val actionsEnabled = InterventionPresentationPolicy.sessionActionsAvailable(request, remaining)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 520.dp
        val fontScale = LocalDensity.current.fontScale
        val showPauseArtwork = maxWidth >= 280.dp && fontScale <= 1.3f
        ResponsiveInterventionScaffold(
            containerColor = CalmPanelTone.INFORMATION.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            keepActionsVisible = true,
            scrimAlpha = if (confirming) 0.54f else 0.66f,
            informationSpacing = TimeManagerSpacing.small,
            actionSpacing = if (confirming && !compactHeight) TimeManagerSpacing.compact else TimeManagerSpacing.small,
            panelPadding = if (confirming && compactHeight) TimeManagerSpacing.medium else TimeManagerSpacing.large,
            header = {
                if (!confirming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showPauseArtwork) {
                            FeatureArtwork(
                                R.drawable.ic_tm_session_pause_refined,
                                Modifier.size(if (compactHeight) 36.dp else 44.dp),
                            )
                        }
                        Text(
                            "先停一下",
                            style = if (compactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            mainContent = {
                if (confirming) {
                    DelayedFocusConfirmation(compactHeight, scheduleError)
                } else {
                    // Countdown and ready state share one region and the same overlay instance.
                    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (!actionsEnabled) remaining.toString() else if (reentry) "稍作停留" else "可以决定了",
                            style = (if (!actionsEnabled) {
                                if (compactHeight) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge
                            } else MaterialTheme.typography.titleLarge).copy(fontFeatureSettings = "tnum"),
                            color = if (!actionsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall)) {
                        Text(
                            if (reentry) "你刚刚已经连续使用过${request.appName}。"
                            else "你已经连续使用${request.appName}${if (request.currentSessionSeconds < 60L) "${request.currentSessionSeconds}秒" else "${request.currentSessionSeconds / 60L}分钟"}。",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            when {
                                reentry -> "如果仍然想继续，请等待片刻再决定。"
                                overlayChoice -> "自动返回桌面暂不可用，可在倒计时结束后选择返回桌面。"
                                else -> "稍微离开屏幕一会儿。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    SessionTodoCard(todoSummary)
                }
            },
            actions = {
                if (confirming) {
                    Button(onClick = {
                        if (!actionsEnabled || scheduling) return@Button
                        scheduling = true
                        scheduleError = null
                        scope.launch {
                            try {
                                checkNotNull(onScheduleDeepFocus) { "此页面暂无法安排静心，请重试。" }.invoke()
                                confirming = false
                                onContinue()
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) {
                                scheduleError = if (error is IllegalStateException) error.message ?: "无法安排静心，请稍后重试。"
                                    else "无法安排静心，请稍后重试。"
                            } finally { scheduling = false }
                        }
                    }, enabled = actionsEnabled && !scheduling, modifier = Modifier.fillMaxWidth()) {
                        Text(if (scheduling) "正在安排…" else "确认安排")
                    }
                    OutlinedButton(onClick = { confirming = false }, enabled = !scheduling, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                } else {
                    if (onScheduleDeepFocus != null) {
                        Button(onClick = { scheduleError = null; confirming = true }, enabled = actionsEnabled,
                            contentPadding = PaddingValues(TimeManagerSpacing.small), modifier = Modifier.fillMaxWidth()) {
                            Text("5 分钟后静心")
                        }
                    }
                    SessionSecondaryActions(
                        homeLabel = if (reentry) "留在桌面" else "返回桌面",
                        enabled = actionsEnabled,
                        onStayHome = onStayHome,
                        onContinue = onContinue,
                    )
                }
            },
        )
    }
}

@Composable
private fun SessionSecondaryActions(
    homeLabel: String,
    enabled: Boolean,
    onStayHome: () -> Unit,
    onContinue: () -> Unit,
) {
    val homeButton: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(
            onClick = onStayHome,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(TimeManagerSpacing.small),
        ) {
            Text(homeLabel, textAlign = TextAlign.Center)
        }
    }
    val continueButton: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(
            onClick = onContinue,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(TimeManagerSpacing.small),
        ) {
            Text("继续使用", textAlign = TextAlign.Center)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 200.dp && LocalDensity.current.fontScale <= 1.3f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
            ) {
                homeButton(Modifier.weight(1f))
                continueButton(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                homeButton(Modifier.fillMaxWidth())
                continueButton(Modifier.fillMaxWidth())
            }
        }
    }
}

internal fun gateRemainingSeconds(endTimeMillis: Long, nowMillis: Long): Long =
    ((endTimeMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
