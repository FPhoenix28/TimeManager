package com.example.timemanager.data.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * A summary of an application's usage during a time period.
 */
@Immutable
data class AppUsage(
    val appName: String,
    val packageName: String,
    val usageTimeMillis: Long,
    val appIcon: Bitmap?,
)
