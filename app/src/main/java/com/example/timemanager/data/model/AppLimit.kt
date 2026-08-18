package com.example.timemanager.data.model

/** A persisted daily usage allowance for one application. */
data class AppLimit(
    val packageName: String,
    val limitMinutes: Int,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        require(limitMinutes >= 0) { "limitMinutes must not be negative." }
    }
}
