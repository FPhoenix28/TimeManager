package com.example.timemanager.permissions

/** UI notice state for this process; permission detection itself has no presentation responsibility. */
class PermissionHealthNoticeTracker {
    private var previousMissing: Set<AppPermission> = emptySet()
    private var pending = false

    fun update(state: PermissionHealthState) {
        val missing = state.missingPermissions
        if ((missing - previousMissing).isNotEmpty()) pending = true
        if (missing.isEmpty()) pending = false
        previousMissing = missing
    }

    fun consumePending(): Boolean = pending.also { pending = false }

    companion object { val process = PermissionHealthNoticeTracker() }
}
