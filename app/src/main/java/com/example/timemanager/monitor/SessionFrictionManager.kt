package com.example.timemanager.monitor

import com.example.timemanager.config.TimeConfig
import com.example.timemanager.data.model.SessionFrictionState
import com.example.timemanager.data.model.SessionGate
import com.example.timemanager.data.model.SessionGatePhase

/** Sole authority for episode usage, progressive interventions, gates and admission. */
class SessionFrictionManager(
    private val clock: () -> Long,
    private val gateDurationMillis: Long = TimeConfig.FRICTION_GATE_SECONDS * 1_000L,
    private val idleResetMillis: Long = TimeConfig.FRICTION_RESET_IDLE_SECONDS * 1_000L,
    private val bypassDurationMillis: Long = TimeConfig.FRICTION_BYPASS_SECONDS * 1_000L,
) {
    private val episodes = mutableMapOf<String, SessionFrictionState>()
    private var actualForegroundPackage: String? = null
    private var physicalPackage: String? = null
    private var lastPhysicalSeconds = 0L
    private var nextGateId = 0L

    @Synchronized fun snapshot(packageName: String): SessionFrictionState? = episodes[packageName]

    @Synchronized fun beginPhysicalSession(packageName: String, firstReminderSeconds: Long) {
        require(packageName.isNotBlank() && firstReminderSeconds > 0L)
        expireIdleEpisodes()
        if (physicalPackage != packageName) lastPhysicalSeconds = 0L
        physicalPackage = packageName
        actualForegroundPackage = packageName
        val state = episodes[packageName] ?: SessionFrictionState(
            packageName = packageName,
            firstReminderSeconds = firstReminderSeconds,
            nextReminderAtSeconds = firstReminderSeconds,
            lastEngagementTimeMillis = clock(),
        )
        episodes[packageName] = state.copy(lastEngagementTimeMillis = clock())
        updateFirstReminder(packageName, firstReminderSeconds)
    }

    @Synchronized fun updateFirstReminder(packageName: String, firstReminderSeconds: Long) {
        require(firstReminderSeconds > 0L)
        val state = episodes[packageName] ?: return
        if (state.reminderCount == 0) {
            episodes[packageName] = state.copy(
                firstReminderSeconds = firstReminderSeconds,
                nextReminderAtSeconds = firstReminderSeconds,
            )
        }
    }

    @Synchronized fun onActualUsage(packageName: String, physicalSeconds: Long) {
        if (physicalPackage != packageName) return
        val state = episodes[packageName] ?: return
        val delta = (physicalSeconds - lastPhysicalSeconds).coerceAtLeast(0L)
        lastPhysicalSeconds = maxOf(lastPhysicalSeconds, physicalSeconds)
        actualForegroundPackage = packageName
        episodes[packageName] = state.copy(
            episodeUsageSeconds = state.episodeUsageSeconds + delta,
            lastEngagementTimeMillis = clock(),
        )
    }

    @Synchronized fun endPhysicalSession(packageName: String, finalPhysicalSeconds: Long) {
        if (physicalPackage != packageName) return
        onActualUsage(packageName, finalPhysicalSeconds)
        physicalPackage = null
        lastPhysicalSeconds = 0L
        onForegroundLeft(packageName)
    }

    @Synchronized fun onForegroundLeft(packageName: String) {
        if (actualForegroundPackage == packageName) actualForegroundPackage = null
        episodes[packageName]?.let { state ->
            episodes[packageName] = state.copy(allowedForeground = false)
        }
    }

    @Synchronized fun onLaunchAttempt(packageName: String) {
        expireIdleEpisodes()
        episodes[packageName]?.let { state ->
            episodes[packageName] = state.copy(lastEngagementTimeMillis = clock())
        }
    }

    @Synchronized fun isReminderDue(packageName: String): Boolean {
        val state = episodes[packageName] ?: return false
        return state.pendingGate == null &&
            state.episodeUsageSeconds >= state.nextReminderAtSeconds
    }

    @Synchronized fun beginGate(packageName: String, phase: SessionGatePhase): SessionGate? {
        val state = episodes[packageName] ?: return null
        state.pendingGate?.let { return it }
        if (phase == SessionGatePhase.REENTRY && !state.reentryGateArmed) return null
        if (phase == SessionGatePhase.REMINDER && !isReminderDue(packageName)) return null
        val gate = SessionGate(++nextGateId, phase, clock() + gateDurationMillis)
        episodes[packageName] = state.copy(
            isActive = true,
            pendingGate = gate,
            bypassExpiresAtMillis = null,
            lastEngagementTimeMillis = clock(),
            reentryGateArmed = phase != SessionGatePhase.REENTRY && state.reentryGateArmed,
        )
        return gate
    }

    @Synchronized fun onReminderShown(packageName: String, gateId: Long, armReentry: Boolean = true) {
        val state = episodes[packageName] ?: return
        val gate = state.pendingGate ?: return
        if (gate.id != gateId || gate.phase != SessionGatePhase.REMINDER || gate.reminderCommitted) return
        val count = state.reminderCount + 1
        episodes[packageName] = state.copy(
            reminderCount = count,
            reentryGateArmed = armReentry,
            nextReminderAtSeconds = state.nextReminderAtSeconds +
                if (count <= 2) 8L * 60L else 5L * 60L,
            pendingGate = gate.copy(reminderCommitted = true),
        )
    }

    @Synchronized fun closeGate(packageName: String, gateId: Long) {
        val state = episodes[packageName] ?: return
        if (state.pendingGate?.id != gateId) return
        episodes[packageName] = state.copy(pendingGate = null, bypassExpiresAtMillis = null)
    }

    /** Explicit Continue from either shown gate; progression/re-entry arming stay unchanged. */
    @Synchronized fun allowNextEntry(packageName: String, gateId: Long): Boolean {
        val state = episodes[packageName] ?: return false
        val gate = state.pendingGate ?: return false
        if (gate.id != gateId || clock() < gate.endTimeMillis) return false
        episodes[packageName] = state.copy(
            pendingGate = null,
            bypassExpiresAtMillis = clock() + bypassDurationMillis,
            lastEngagementTimeMillis = clock(),
        )
        return true
    }

    @Synchronized fun consumeBypass(packageName: String): Boolean {
        val state = episodes[packageName] ?: return false
        val expiry = state.bypassExpiresAtMillis ?: return false
        if (clock() >= expiry) {
            episodes[packageName] = state.copy(bypassExpiresAtMillis = null)
            return false
        }
        actualForegroundPackage = packageName
        episodes[packageName] = state.copy(
            bypassExpiresAtMillis = null,
            allowedForeground = true,
            lastEngagementTimeMillis = clock(),
        )
        return true
    }

    @Synchronized fun cancelBypass(packageName: String) {
        episodes[packageName]?.let { episodes[packageName] = it.copy(bypassExpiresAtMillis = null) }
    }

    /** Lose enhanced admission, not the episode or an already shown ordinary reminder. */
    @Synchronized fun onAccessibilityDisconnected() {
        episodes.replaceAll { _, state ->
            state.copy(
                reentryGateArmed = false,
                bypassExpiresAtMillis = null,
                allowedForeground = false,
                pendingGate = state.pendingGate?.takeUnless { it.phase == SessionGatePhase.REENTRY },
            )
        }
    }

    @Synchronized fun isEntryAdmitted(packageName: String): Boolean =
        episodes[packageName]?.allowedForeground == true

    @Synchronized fun isReentryGateArmed(packageName: String): Boolean =
        episodes[packageName]?.let { it.reentryGateArmed && it.pendingGate == null } == true

    @Synchronized fun expireIdleEpisodes(): Set<String> {
        val now = clock()
        val expired = episodes.values.filter { state ->
            state.pendingGate == null && state.packageName != actualForegroundPackage &&
                now - state.lastEngagementTimeMillis >= idleResetMillis
        }.map { it.packageName }.toSet()
        expired.forEach(episodes::remove)
        return expired
    }

    @Synchronized fun retainManagedApps(packageNames: Set<String>) {
        episodes.keys.retainAll(packageNames)
        if (physicalPackage !in packageNames) {
            physicalPackage = null
            lastPhysicalSeconds = 0L
        }
        if (actualForegroundPackage !in packageNames) actualForegroundPackage = null
    }

    @Synchronized fun failOpen(packageName: String) {
        episodes.remove(packageName)
        if (physicalPackage == packageName) physicalPackage = null
        if (actualForegroundPackage == packageName) actualForegroundPackage = null
    }

    @Synchronized fun clear() {
        episodes.clear()
        physicalPackage = null
        actualForegroundPackage = null
        lastPhysicalSeconds = 0L
    }
}
