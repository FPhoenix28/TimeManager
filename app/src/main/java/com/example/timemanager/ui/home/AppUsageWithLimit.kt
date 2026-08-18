package com.example.timemanager.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/** Home-screen representation of usage with an optional daily allowance. */
@Immutable
data class AppUsageWithLimit(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val usedMinutes: Long,
    val limitMinutes: Int?,
)
