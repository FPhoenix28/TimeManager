package com.example.timemanager.data.model

/** Persisted soft-limit state for one app in one 04:00-based usage day. */
data class InterventionState(
    val packageName: String,
    val usageDayKey: String,
    val gracePeriodEndTime: Long,
    val extensionsUsed: Int,
)
