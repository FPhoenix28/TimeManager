package com.example.timemanager.accessibility.rules

import android.content.Context
import android.os.SystemClock
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.preferences.*
import com.example.timemanager.data.repository.*
import com.example.timemanager.domain.intervention.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthManager
import com.example.timemanager.utils.UsageDayProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Service-scoped readonly configuration mirror, shared by production decisions and Debug comparison. */
class AccessibilityRulesFacts(context: Context, private val coordinator: InterventionCoordinator, scope: CoroutineScope) {
    private val app = context.applicationContext
    private val health = PermissionHealthManager.get(app)
    private val day = UsageDayProvider()
    private var revision = coordinator.currentAccessibilityPolicyRevision()
    private var sequence = 0L
    private var loaded = false
    private var managed = emptySet<String>()
    private var limits = emptyMap<String, Long>()
    private var sessions = emptyMap<String, Long>()
    private var focus = FocusModeState()
    private val job: Job = scope.launch {
        combine(TrackedAppRepository(TrackedAppPreferences(app)).observeTrackedPackageNames(),
            LimitRepository(AppLimitPreferences(app)).observeAllLimits(),
            SessionSettingsRepository(SessionSettingsPreferences(app)).observeStoredSeconds()) { packages, quotas, reminders ->
            Triple(packages, quotas.associate { it.packageName to it.limitSeconds }, reminders)
        }.collect { (packages, quotas, reminders) ->
            managed = packages; limits = quotas; sessions = reminders; loaded = true
            changed()
        }
    }
    fun onFocusChanged(state: FocusModeState) { if (focus != state) { focus = state; changed() } }
    private fun changed() { revision++; coordinator.updateAccessibilityPolicyRevision(revision) }
    fun close() { job.cancel() }

    fun snapshot(target: TargetFacts, state: FocusModeState, scope: AccessibilityObservationScope,
        entry: ForegroundEntry? = null, prepared: PreparedAccessibilityEntry? = null): AccessibilityRulesSnapshot {
        val pkg = target.packageName
        val capabilities = health.state.value
        val identity = EvaluationIdentity(pkg, ForegroundGeneration(entry?.generation ?: coordinator.foregroundObservation()?.generation ?: 0L),
            PolicyRevision(revision), UsageDayKey(day.currentUsageDay().key), ++sequence)
        return AccessibilityRulesSnapshot(identity, target, EvaluationTime(System.currentTimeMillis(), SystemClock.elapsedRealtime()),
            state, AccessibilityPolicyFacts(loaded && (pkg in managed) == coordinator.isManagedForRules(pkg),
                coordinator.isManagedForRules(pkg), limits[pkg], sessions[pkg]),
            prepared?.publication?.snapshot ?: coordinator.dailySnapshot(pkg),
            prepared != null && prepared.entry == entry && prepared.policyRevision == revision &&
                coordinator.preparedEntryIsCurrent(prepared, identity.usageDayKey.value),
            coordinator.friction.snapshot(pkg), capabilities, scope, coordinator.hasOverlay(),
            capabilityInconsistent = capabilities.accessibilityConnected != coordinator.accessibilityConnected)
    }

    fun isCurrent(input: AccessibilityRulesSnapshot): Boolean =
        revision == input.identity.policyRevision.value && day.currentUsageDay().key == input.identity.usageDayKey.value &&
            (coordinator.foregroundObservation()?.generation ?: 0L) == input.identity.foregroundGeneration.value &&
            coordinator.accessibilityConnected && focus == input.focus &&
            (!(input.focus.isActive(input.time.wallNowMillis) || input.focus.isDeepFocusActive(input.time.wallNowMillis)) ||
                input.focus.isActive() || input.focus.isDeepFocusActive())
}
