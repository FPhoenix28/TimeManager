package com.example.timemanager.domain.intervention

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.domain.focus.FocusEnforcementPolicy
import com.example.timemanager.monitor.SessionFrictionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class InterventionPriority { SESSION, DAILY, FOCUS, DEEP }
enum class EntryCheckResult { CHECK_FRICTION, HANDLED, UNAVAILABLE }
enum class SessionReminderMode { HOME, OVERLAY_ONLY }

data class ForegroundEntry(val packageName: String, val generation: Long)
data class DailyLimitSnapshot(
    val usageDayKey: String,
    val usedSeconds: Long,
    val effectiveLimitSeconds: Long?,
    val isOverLimit: Boolean,
)

/** Publication metadata only; the Daily snapshot still has its single existing owner/map. */
data class DailyReadObservation(val snapshot: DailyLimitSnapshot, val entry: ForegroundEntry?, val sequence: Long)

/** Facts-only reply. Does not imply that Daily, admission or a Gate was executed. */
data class PreparedAccessibilityEntry(val entry: ForegroundEntry, val publication: DailyReadObservation,
    val baseLimitSeconds: Long?, val policyRevision: Long)

enum class AccessibilityEntryAction { ALLOW, DAILY, SESSION, FAIL_OPEN }

sealed interface MonitorInterventionCommand {
    data class PrepareEntry(val entry: ForegroundEntry, val revision: Long,
        val result: CompletableDeferred<PreparedAccessibilityEntry?>) : MonitorInterventionCommand
    data class ExecuteEntry(val facts: PreparedAccessibilityEntry, val action: AccessibilityEntryAction) : MonitorInterventionCommand
    data class CheckEntry(val entry: ForegroundEntry, val result: CompletableDeferred<EntryCheckResult>) : MonitorInterventionCommand
    data class AcceptedEntry(val entry: ForegroundEntry) : MonitorInterventionCommand
    data class ShowReentryGate(val entry: ForegroundEntry) : MonitorInterventionCommand
    data class ForegroundLeft(val packageName: String) : MonitorInterventionCommand
    data class DismissForFocus(val result: CompletableDeferred<Boolean>) : MonitorInterventionCommand
}

data class HomeCommand(
    val packageName: String,
    val result: CompletableDeferred<Boolean>,
    val confirmLauncher: Boolean = false,
)

/** Process state + lifecycle-collected messages only: never retains a Service, Context or View. */
class InterventionCoordinator(
    val friction: SessionFrictionManager,
    private val ownPackage: String,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val monitorEvents = MutableSharedFlow<MonitorInterventionCommand>(extraBufferCapacity = 16)
    private val accessibilityEvents = MutableSharedFlow<HomeCommand>(extraBufferCapacity = 4)
    val monitorCommands = monitorEvents.asSharedFlow()
    val accessibilityCommands = accessibilityEvents.asSharedFlow()
    private val daily = mutableMapOf<String, DailyLimitSnapshot>()
    private val dailyPublications = mutableMapOf<String, Pair<ForegroundEntry?, Long>>()
    private var dailyPublicationSequence = 0L
    private var managedPackages = emptySet<String>()
    private var focusState = FocusModeState()
    private var foregroundPackage: String? = null
    private val lastStablePackage = MutableStateFlow<String?>(null)
    private var sessionHomeLauncher: String? = null
    private var hasForegroundObservation = false
    private var homeTransitionTarget: String? = null
    private var pendingEntry: ForegroundEntry? = null
    private var generation = 0L
    private var failedOpenVisit: String? = null
    private var overlayOwner: Pair<InterventionPriority, String>? = null
    private val mutableAccessibilityConnection = MutableStateFlow(false)
    val accessibilityConnection = mutableAccessibilityConnection.asStateFlow()
    val accessibilityConnected: Boolean get() = mutableAccessibilityConnection.value
    private var overlayPausesTiming = false
    private var accessibilityPolicyRevision = 0L
    private var executedEntry: ForegroundEntry? = null
    private val evaluatedFocusTargets = mutableMapOf<String, Pair<FocusModeState, Boolean>>()

    @Synchronized fun updateAccessibilityPolicyRevision(revision: Long) { accessibilityPolicyRevision = revision }
    @Synchronized fun currentAccessibilityPolicyRevision(): Long = accessibilityPolicyRevision
    @Synchronized fun publishAccessibilityFocusSelection(packageName: String, state: FocusModeState, blocked: Boolean) {
        evaluatedFocusTargets[packageName] = state to blocked
    }

    @Synchronized fun setAccessibilityConnected(connected: Boolean, launcherPackage: String? = null) {
        if (connected && launcherPackage != null) sessionHomeLauncher = launcherPackage
        if (accessibilityConnected == connected) return
        mutableAccessibilityConnection.value = connected
        hasForegroundObservation = false
        homeTransitionTarget = null
        if (!connected) {
            evaluatedFocusTargets.clear()
            foregroundPackage?.let(friction::onForegroundLeft)
            foregroundPackage = null
            lastStablePackage.value = null
            sessionHomeLauncher = null
            pendingEntry = null
            failedOpenVisit = null
            friction.onAccessibilityDisconnected()
        }
    }

    @Synchronized fun updateManagedPackages(packages: Set<String>) {
        managedPackages = packages.toSet()
        daily.keys.retainAll(packages)
        dailyPublications.keys.retainAll(packages)
        friction.retainManagedApps(packages)
    }

    @Synchronized fun updateFocus(state: FocusModeState) {
        if (focusState != state) evaluatedFocusTargets.clear()
        focusState = state
    }

    @Synchronized fun isFocusBlocking(packageName: String): Boolean {
        val evaluated = evaluatedFocusTargets[packageName]
        if (evaluated?.first == focusState &&
            (focusState.isActive(wallClock()) || focusState.isDeepFocusActive(wallClock()))) return evaluated.second
        return focusState.isDeepFocusActive(wallClock()) ||
            FocusEnforcementPolicy.shouldBlock(focusState, packageName, ownPackage, wallClock())
    }

    @Synchronized fun publishDaily(packageName: String, snapshot: DailyLimitSnapshot, entry: ForegroundEntry? = null) {
        daily[packageName] = snapshot
        dailyPublications[packageName] = (entry ?: foregroundObservation()?.takeIf { it.packageName == packageName }) to ++dailyPublicationSequence
    }

    @Synchronized fun dailySnapshot(packageName: String): DailyLimitSnapshot? = daily[packageName]

    @Synchronized fun readDailyObservation(packageName: String): DailyReadObservation? {
        val snapshot = daily[packageName] ?: return null
        val publication = dailyPublications[packageName] ?: return null
        return DailyReadObservation(snapshot, publication.first, publication.second)
    }

    @Synchronized fun isManagedForRules(packageName: String): Boolean = packageName in managedPackages

    /** Called only for real package transitions, not own-overlay/System UI window changes. */
    @Synchronized fun onForegroundChanged(packageName: String?): ForegroundEntry? {
        if (packageName != null && shouldIgnoreHomeEcho(packageName)) return null
        if (foregroundPackage == packageName) return null
        hasForegroundObservation = true
        foregroundPackage?.let { previous ->
            friction.onForegroundLeft(previous)
            monitorEvents.tryEmit(MonitorInterventionCommand.ForegroundLeft(previous))
        }
        foregroundPackage = packageName
        lastStablePackage.value = packageName
        failedOpenVisit = null
        generation++
        pendingEntry = null
        if (packageName == null || packageName !in managedPackages) return null
        friction.onLaunchAttempt(packageName)
        return ForegroundEntry(packageName, generation).also { pendingEntry = it }
    }

    /** Popup/window events alone cannot establish an external app boundary. */
    @Synchronized fun onForegroundWindow(packageName: String, stableAppWindow: Boolean): ForegroundEntry? =
        if (stableAppWindow) onForegroundChanged(packageName) else null

    @Synchronized fun stableForegroundPackage(): String? = lastStablePackage.value

    /** Observation only: exposes the existing generation without establishing a new visit. */
    @Synchronized fun foregroundObservation(): ForegroundEntry? =
        foregroundPackage?.let { ForegroundEntry(it, generation) }

    @Synchronized fun awaitingStableExit(packageName: String): Boolean = homeTransitionTarget == packageName

    @Synchronized fun markHomeRequested(packageName: String) {
        if (foregroundPackage == packageName) homeTransitionTarget = packageName
    }

    @Synchronized fun cancelHomeRequest(packageName: String) {
        if (homeTransitionTarget == packageName) homeTransitionTarget = null
    }

    @Synchronized fun isCurrent(entry: ForegroundEntry): Boolean =
        foregroundPackage == entry.packageName && generation == entry.generation

    @Synchronized fun isObservedForeground(packageName: String): Boolean =
        foregroundPackage == packageName

    @Synchronized fun canTime(packageName: String): Boolean =
        !overlayPausesTiming && (!accessibilityConnected || !hasForegroundObservation ||
            foregroundPackage == packageName && pendingEntry == null)

    @Synchronized fun pauseTimingForOverlay(priority: InterventionPriority, packageName: String) {
        if (overlayOwner == (priority to packageName)) overlayPausesTiming = true
    }

    @Synchronized fun isTimingPausedForOverlay(): Boolean = overlayPausesTiming

    /** Wait for a real launcher/other-app boundary, not delayed window echoes after HOME. */
    @Synchronized fun shouldIgnoreHomeEcho(packageName: String): Boolean {
        if (homeTransitionTarget != packageName) {
            homeTransitionTarget = null
            return false
        }
        return true
    }

    @Synchronized fun allowEntry(entry: ForegroundEntry): Boolean {
        if (!isCurrent(entry)) return false
        pendingEntry = null
        monitorEvents.tryEmit(MonitorInterventionCommand.AcceptedEntry(entry))
        return true
    }

    @Synchronized fun failOpenVisit(entry: ForegroundEntry) {
        if (!isCurrent(entry)) return
        failedOpenVisit = entry.packageName
        allowEntry(entry)
    }

    @Synchronized fun interventionsAllowed(packageName: String): Boolean =
        (!accessibilityConnected || !isFocusBlocking(packageName)) && failedOpenVisit != packageName

    @Synchronized fun enhancedInterventionsAllowed(packageName: String): Boolean =
        accessibilityConnected && interventionsAllowed(packageName)

    @Synchronized fun sessionReminderMode(packageName: String): SessionReminderMode? = when {
        !interventionsAllowed(packageName) -> null
        accessibilityConnected -> SessionReminderMode.HOME
        else -> SessionReminderMode.OVERLAY_ONLY
    }

    @Synchronized fun suppressCurrentVisit(packageName: String) {
        if (foregroundPackage == packageName) {
            failedOpenVisit = packageName
            pendingEntry = null
        }
    }

    @Synchronized fun onHomeSucceeded(packageName: String) {
        if (foregroundPackage != packageName) return
        friction.onForegroundLeft(packageName)
        homeTransitionTarget = packageName
        foregroundPackage = null
        pendingEntry = null
        generation++
    }

    @Synchronized fun claimOverlay(priority: InterventionPriority, packageName: String): Boolean {
        val current = overlayOwner
        if (current != null && current != (priority to packageName)) return false
        if (priority <= InterventionPriority.DAILY && accessibilityConnected && isFocusBlocking(packageName)) return false
        overlayOwner = priority to packageName
        return true
    }

    @Synchronized fun releaseOverlay(priority: InterventionPriority, packageName: String) {
        if (overlayOwner == (priority to packageName)) {
            overlayOwner = null
            overlayPausesTiming = false
        }
    }

    @Synchronized fun hasOverlay(): Boolean = overlayOwner != null

    @Synchronized fun hasLowerOverlay(): Boolean =
        overlayOwner?.first?.let { it <= InterventionPriority.DAILY } == true

    @Synchronized fun entryPriority(packageName: String): InterventionPriority? = when {
        focusState.isDeepFocusActive(wallClock()) -> InterventionPriority.DEEP
        FocusEnforcementPolicy.shouldBlock(focusState, packageName, ownPackage, wallClock()) -> InterventionPriority.FOCUS
        daily[packageName]?.isOverLimit == true -> InterventionPriority.DAILY
        friction.isReentryGateArmed(packageName) -> InterventionPriority.SESSION
        else -> null
    }

    suspend fun checkEntry(entry: ForegroundEntry): EntryCheckResult {
        if (monitorEvents.subscriptionCount.value == 0) return EntryCheckResult.UNAVAILABLE
        val response = CompletableDeferred<EntryCheckResult>()
        monitorEvents.emit(MonitorInterventionCommand.CheckEntry(entry, response))
        return withTimeoutOrNull(COMMAND_TIMEOUT_MILLIS) { response.await() }
            ?: EntryCheckResult.UNAVAILABLE.also { response.cancel() }
    }

    suspend fun prepareEntryFacts(entry: ForegroundEntry, revision: Long): PreparedAccessibilityEntry? {
        if (monitorEvents.subscriptionCount.value == 0) return null
        val response = CompletableDeferred<PreparedAccessibilityEntry?>()
        monitorEvents.emit(MonitorInterventionCommand.PrepareEntry(entry, revision, response))
        return withTimeoutOrNull(COMMAND_TIMEOUT_MILLIS) { response.await() }
            .also { if (!response.isCompleted) response.cancel() }
    }

    @Synchronized fun preparedEntryIsCurrent(facts: PreparedAccessibilityEntry, usageDayKey: String): Boolean =
        isCurrent(facts.entry) && facts.policyRevision == accessibilityPolicyRevision &&
            facts.publication.entry == facts.entry && facts.publication.snapshot.usageDayKey == usageDayKey &&
            readDailyObservation(facts.entry.packageName) == facts.publication

    /** Called at execution, not evaluation. A real generation can be dispatched at most once. */
    @Synchronized fun claimPreparedEntry(facts: PreparedAccessibilityEntry, usageDayKey: String): Boolean {
        if (!preparedEntryIsCurrent(facts, usageDayKey) || executedEntry == facts.entry) return false
        executedEntry = facts.entry
        return true
    }

    /** Discard stale work without admitting a visit or consuming Episode/Gate/token state. */
    @Synchronized fun discardEntryPreparation(entry: ForegroundEntry) {
        if (isCurrent(entry) && pendingEntry == entry) pendingEntry = null
    }

    suspend fun executePreparedEntry(facts: PreparedAccessibilityEntry, action: AccessibilityEntryAction) {
        monitorEvents.emit(MonitorInterventionCommand.ExecuteEntry(facts, action))
    }

    suspend fun requestReentryGate(entry: ForegroundEntry) {
        monitorEvents.emit(MonitorInterventionCommand.ShowReentryGate(entry))
    }

    suspend fun requestHome(packageName: String): Boolean {
        if (!accessibilityConnected || accessibilityEvents.subscriptionCount.value == 0) return false
        val response = CompletableDeferred<Boolean>()
        accessibilityEvents.emit(HomeCommand(packageName, response))
        return withTimeoutOrNull(COMMAND_TIMEOUT_MILLIS) { response.await() }
            ?: false.also { response.cancel() }
    }

    /** Session-only: API acknowledgement is not proof that the launcher is visible. */
    suspend fun requestSessionHome(packageName: String): Boolean {
        val launcher = synchronized(this) { sessionHomeLauncher } ?: return false
        if (!accessibilityConnected || accessibilityEvents.subscriptionCount.value == 0) return false
        if (lastStablePackage.value == launcher) return true
        val response = CompletableDeferred<Boolean>()
        var confirmed = false
        try {
            confirmed = withTimeoutOrNull(COMMAND_TIMEOUT_MILLIS) {
                accessibilityEvents.emit(HomeCommand(packageName, response, confirmLauncher = true))
                response.await() && lastStablePackage.first {
                    it == launcher || !accessibilityConnected
                } == launcher
            } ?: false
            return confirmed
        } finally {
            if (!confirmed) {
                response.cancel()
                cancelHomeRequest(packageName)
            }
        }
    }

    suspend fun dismissLowerForFocus(): Boolean {
        if (monitorEvents.subscriptionCount.value == 0) return !hasLowerOverlay()
        val response = CompletableDeferred<Boolean>()
        monitorEvents.emit(MonitorInterventionCommand.DismissForFocus(response))
        return withTimeoutOrNull(COMMAND_TIMEOUT_MILLIS) { response.await() }
            ?: false.also { response.cancel() }
    }

    @Synchronized fun monitorStopped() {
        daily.clear()
        dailyPublications.clear()
        managedPackages = emptySet()
        pendingEntry = null
        if (overlayOwner?.first?.let { it <= InterventionPriority.DAILY } == true) overlayOwner = null
        overlayPausesTiming = false
        friction.clear()
    }

    private companion object { const val COMMAND_TIMEOUT_MILLIS = 2_000L }
}
