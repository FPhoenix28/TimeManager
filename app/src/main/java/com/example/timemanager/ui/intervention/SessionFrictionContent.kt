package com.example.timemanager.ui.intervention

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.domain.intervention.InterventionPresentation
import com.example.timemanager.domain.intervention.InterventionPresentationPolicy
import kotlinx.coroutines.delay

/** All admission/progression state lives outside Compose; layout changes only move content. */
@Composable
internal fun SessionFrictionContent(
    request: LimitInterventionRequest,
    onStayHome: () -> Unit,
    onContinue: () -> Unit,
    todoSummary: TodoSummary = TodoSummary(),
) {
    val gate = request.sessionGate ?: return
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
    ResponsiveInterventionScaffold(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        header = {
            Text("先停一下", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        mainContent = {
            // Countdown and ready state share one region and the same overlay instance.
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (!actionsEnabled) remaining.toString() else if (reentry) "稍作停留" else "可以决定了",
                    style = if (!actionsEnabled) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
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
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            TodoOverlaySummary(todoSummary)
        },
        actions = {
            Button(onClick = onContinue, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("继续使用")
            }
            OutlinedButton(onClick = onStayHome, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (reentry) "留在桌面" else "返回桌面")
            }
        },
    )
}

internal fun gateRemainingSeconds(endTimeMillis: Long, nowMillis: Long): Long =
    ((endTimeMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
