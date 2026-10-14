package com.example.timemanager.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Display formatting only; the repositories continue to use seconds. */
fun formatUsageDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = safe % 3_600L / 60L
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        safe in 1..59 -> "${safe}秒"
        else -> "${minutes}分钟"
    }
}

@Composable
fun UsageDurationText(seconds: Long, modifier: Modifier = Modifier) {
    val duration = formatUsageDuration(seconds)
    val text = buildAnnotatedString {
        Regex("\\d+|\\D+").findAll(duration).forEach { part ->
            val numeric = part.value.first().isDigit()
            withStyle(SpanStyle(
                fontSize = if (numeric) 44.sp else 22.sp,
                fontWeight = if (numeric) FontWeight.Medium else FontWeight.Normal,
            )) { append(part.value) }
        }
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.displaySmall.copy(
        lineHeight = 56.sp, fontFeatureSettings = "tnum",
    ))
}
