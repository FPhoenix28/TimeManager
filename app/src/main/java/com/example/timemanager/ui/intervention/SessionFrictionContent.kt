package com.example.timemanager.ui.intervention

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.todo.TodoSummary
import kotlinx.coroutines.delay

/** All admission/progression state lives outside Compose; layout changes only move content. */
@Composable
internal fun SessionFrictionContent(
    request: LimitInterventionRequest,
    onStayHome: () -> Unit,
    onContinue: () -> Unit,
    onCountdownFinished: () -> Unit,
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
    val latestFinished by rememberUpdatedState(onCountdownFinished)
    LaunchedEffect(gate.id, remaining == 0L) {
        if (remaining == 0L && gate.phase == SessionGatePhase.REMINDER) latestFinished()
    }
    val reentry = gate.phase == SessionGatePhase.REENTRY
    ResponsiveInterventionScaffold(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        header = { Text("先停一下", style = MaterialTheme.typography.headlineMedium) },
        mainContent = {
            Text(
                if (remaining > 0L) remaining.toString() else "稍作停留",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                if (reentry) "你刚刚已经连续使用过${request.appName}。"
                else "你已经连续使用${request.appName}${if (request.currentSessionSeconds < 60L) "${request.currentSessionSeconds}秒" else "${request.currentSessionSeconds / 60L}分钟"}。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                if (reentry) "如果仍然想继续，请等待片刻再决定。" else "稍微离开屏幕一会儿。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TodoOverlaySummary(todoSummary)
        },
        actions = {
            if (reentry) {
                Button(onClick = onContinue, enabled = remaining == 0L, modifier = Modifier.fillMaxWidth()) {
                    Text("继续使用")
                }
                OutlinedButton(onClick = onStayHome, enabled = remaining == 0L, modifier = Modifier.fillMaxWidth()) {
                    Text("留在桌面")
                }
            }
        },
    )
}

internal fun gateRemainingSeconds(endTimeMillis: Long, nowMillis: Long): Long =
    ((endTimeMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
