package com.example.timemanager.data.model

/** Persisted real-time estimate for one application on one calendar date. */
data class RealtimeUsageRecord(
    val packageName: String,
    val date: String,
    val seconds: Long,
)
