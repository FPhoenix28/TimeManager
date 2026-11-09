package com.example.timemanager.ui.intervention

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
internal fun DelayedFocusConfirmation(compactHeight: Boolean, error: String?) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) TimeManagerSpacing.extraSmall else TimeManagerSpacing.small),
    ) {
        CalmLotusIcon(Modifier.size(if (compactHeight) 36.dp else 64.dp))
        Text(
            "准备开始静心",
            style = if (compactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "给自己一点时间，结束手头的事情。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = if (compactHeight) 0.dp else TimeManagerSpacing.small),
            shape = MaterialTheme.shapes.large,
            color = colors.primary.copy(alpha = 0.08f),
            contentColor = colors.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compactHeight) TimeManagerSpacing.small else TimeManagerSpacing.compact,
                    vertical = if (compactHeight) TimeManagerSpacing.extraSmall else TimeManagerSpacing.small,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall),
            ) {
                Text("5 分钟后自动开始", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text("静心持续 30 分钟", style = MaterialTheme.typography.titleMedium,
                    color = colors.primary, textAlign = TextAlign.Center)
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, textAlign = TextAlign.Center)
        }
    }
}

/** Decorative artwork for this confirmation only, drawn in a 72-unit square. */
@Composable
private fun CalmLotusIcon(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val sidePetal = remember {
        Path().apply {
            moveTo(36f, 55f)
            cubicTo(20f, 59f, 8f, 48f, 9f, 33f)
            cubicTo(22f, 31f, 35f, 39f, 36f, 55f)
            close()
        }
    }
    val centerPetal = remember {
        Path().apply {
            moveTo(36f, 55f)
            cubicTo(21f, 46f, 23f, 26f, 36f, 15f)
            cubicTo(49f, 26f, 51f, 46f, 36f, 55f)
            close()
        }
    }
    Canvas(modifier) {
        scale(size.width / 72f, size.height / 72f, pivot = Offset.Zero) {
            val center = Offset(36f, 36f)
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0f)), center, 36f),
                radius = 36f,
                center = center,
            )
            val sideFill = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.04f)), 32f, 56f,
            )
            val outline = Stroke(width = 1.25f, join = StrokeJoin.Round)
            drawPath(sidePetal, sideFill)
            drawPath(sidePetal, accent.copy(alpha = 0.58f), style = outline)
            scale(-1f, 1f, pivot = center) {
                drawPath(sidePetal, sideFill)
                drawPath(sidePetal, accent.copy(alpha = 0.58f), style = outline)
            }
            drawPath(
                centerPetal,
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.06f)), 15f, 55f),
            )
            drawPath(centerPetal, accent.copy(alpha = 0.9f), style = outline)
        }
    }
}
