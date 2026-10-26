package com.example.timemanager.ui.detail

import androidx.compose.runtime.Composable
import com.example.timemanager.ui.components.formatUsageDuration

fun formatAppDetailDuration(seconds: Long): String = formatUsageDuration(seconds)

@Composable
fun AppDetailTimingControls() = Unit

@Composable
fun AppDetailDurationDialog(
    @Suppress("UNUSED_PARAMETER") title: String,
    @Suppress("UNUSED_PARAMETER") initialSeconds: Long,
    @Suppress("UNUSED_PARAMETER") onSave: suspend (Long) -> String?,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit,
) = Unit
