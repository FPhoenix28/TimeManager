package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.SessionFrictionState
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.focus.DeepFocusDisposition
import com.example.timemanager.domain.focus.DeepFocusEnforcementPolicy
import com.example.timemanager.domain.focus.DeepFocusEssentialApps
import com.example.timemanager.domain.focus.FocusEnforcementPolicy
import com.example.timemanager.domain.limit.LimitInterventionTriggerState
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.profile.DailyRuleConfig
import com.example.timemanager.monitor.SessionFrictionManager
import com.example.timemanager.service.selectInterventionType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Expected facts are computed by the existing v0.9.0 helpers, not copied from the new engine. */
@RunWith(Parameterized::class)
class RulesParityTest(private val case: Case) {
    data class Case(
        val name: String,
        val context: RuleEvaluationContext,
        val expectedKind: RuleKind?,
        val expectedReason: CandidateReason? = null,
    ) {
        override fun toString(): String = name
    }

    @Test fun sameFactsMatchLegacyHelperWithoutChangingTheSuppliedSnapshots() {
        val before = case.context.copy()
        val result = RulesEvaluator().evaluate(case.context)
        assertEquals(case.name, case.expectedKind, result.selected?.kind)
        if (case.expectedReason != null) assertEquals(case.expectedReason, result.selected?.reason)
        assertEquals(before, case.context)
        assertEquals(before.identity, result.identity)
    }

    companion object {
        private val f = V090CompatibilityFixtures

        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Case>> = (dailyCases() + dailyEdgeCases() + focusCases() + deepCases() +
            sessionCases() + entryCases() + priorityCases()).map { arrayOf(it) }

        private fun dailyContext(base: Long, used: Long, extension: Long = 0L): RuleEvaluationContext {
            val context = f.context()
            return context.copy(session = null, daily = f.daily(used, extension),
                effectivePolicy = context.effectivePolicy.copy(appPolicy = context.effectivePolicy.appPolicy.copy(
                    daily = DailyRuleConfig.Explicit(base))))
        }

        // UsageLimitEvaluator.evaluateSeconds: includes the enforcement-specific 0/0 = not reached.
        private fun dailyCases(): List<Case> = buildList {
            for (base in listOf(0L, 600L)) for (extension in listOf(0L, 900L, 1_800L)) {
                val effective = base + extension
                for (used in listOf(0L, (effective - 1L).coerceAtLeast(0L), effective, effective + 1L).distinct()) {
                    val old = UsageLimitEvaluator().evaluateSeconds(f.PACKAGE, used, effective)
                    add(Case("daily base=$base extension=$extension used=$used", dailyContext(base, used, extension),
                        if (old.isLimitReached) RuleKind.DAILY else null))
                }
            }
        }

        // LimitInterventionTriggerStateTest's crossing/entry/extension/day semantics, same old helper.
        private fun dailyEdgeCases(): List<Case> = buildList {
            data class Step(val used: Long, val extension: Long = 0L, val entered: Boolean = false,
                val nextDay: Boolean = false, val extended: Boolean = false)
            val oldTrigger = LimitInterventionTriggerState()
            val steps = listOf(Step(599L), Step(600L), Step(601L), Step(601L, entered = true),
                Step(600L, 900L, extended = true), Step(1_499L, 900L), Step(1_500L, 900L),
                Step(1_501L, 900L, entered = true), Step(0L, nextDay = true), Step(600L, nextDay = true))
            steps.forEachIndexed { index, step ->
                val day = if (step.nextDay) UsageDayKey("2026-10-23") else f.usageDay
                if (step.extended) oldTrigger.onLimitExtended(f.PACKAGE, day.value)
                if (step.entered) oldTrigger.onManagedAppEntered(f.PACKAGE)
                val reached = UsageLimitEvaluator().evaluateSeconds(f.PACKAGE, step.used, 600L + step.extension).isLimitReached
                val old = oldTrigger.evaluate(f.PACKAGE, day.value, reached)
                val reason = when {
                    old.limitCrossed -> DailyCandidateReason.LIMIT_CROSSED
                    old.enteredWhileOverLimit -> DailyCandidateReason.ENTERED_WHILE_OVER_LIMIT
                    else -> null
                }
                val base = dailyContext(600L, step.used, step.extension)
                val context = base.copy(identity = base.identity.copy(usageDayKey = day), daily = base.daily!!.copy(
                    identity = UsageDayRuntimeIdentity(f.PACKAGE, day),
                    edge = DailyEdgeSnapshot(old.previousOverLimit, step.entered)))
                add(Case("daily edge step $index", context, if (reached && reason != null) RuleKind.DAILY else null, reason))
            }
        }

        private fun focusCases(): List<Case> = buildList {
            data class Input(val name: String, val now: Long = 10_000L, val selected: Boolean = true,
                val own: Boolean = false, val status: FocusModeStatus = FocusModeStatus.ACTIVE)
            for (input in listOf(Input("active"), Input("before", now = 999L), Input("end", now = 61_000L),
                Input("completed", status = FocusModeStatus.COMPLETED), Input("not selected", selected = false), Input("own", own = true))) {
                val state = FocusModeState(FocusModeType.FOCUS, input.status, 1_000L, 61_000L,
                    if (input.selected) setOf(f.PACKAGE) else emptySet())
                val expected = FocusEnforcementPolicy.shouldBlock(state, f.PACKAGE,
                    if (input.own) f.PACKAGE else "example.own", input.now)
                val base = f.context()
                val context = base.copy(daily = f.daily(0L), session = null, time = EvaluationTime(input.now, 1_000L),
                    target = base.target.copy(disposition = if (input.own) TargetDisposition.OWN_APP else TargetDisposition.ORDINARY_APP),
                    focus = FocusRuntimeSnapshot.Focus(1_000L, 61_000L, state.blockedPackages, input.status))
                add(Case("focus ${input.name}", context, if (expected) RuleKind.FOCUS else null))
            }
        }

        private fun deepCases(): List<Case> = buildList {
            // Class resolution belongs to a later adapter. These rows pair resolved TargetFacts
            // with real recognized class/package inputs of DeepFocusEnforcementPolicyTest.
            fun addDeep(name: String, disposition: TargetDisposition, now: Long = 10_000L,
                status: FocusModeStatus = FocusModeStatus.ACTIVE, dialerMain: Boolean = false) {
                val essentials = DeepFocusEssentialApps(
                    ownPackage = if (disposition == TargetDisposition.OWN_APP) f.PACKAGE else "example.own",
                    launcherPackage = if (disposition == TargetDisposition.LAUNCHER) f.PACKAGE else "launcher",
                    inputMethodPackage = if (disposition == TargetDisposition.INPUT_METHOD) f.PACKAGE else "ime",
                    defaultDialerPackage = f.PACKAGE,
                    callUiPackages = setOf(f.PACKAGE),
                    settingsPackage = if (disposition == TargetDisposition.RECOVERY_SETTINGS_UI) f.PACKAGE else "settings",
                    systemUiPackages = if (disposition == TargetDisposition.SYSTEM_UI) setOf(f.PACKAGE) else setOf("systemui"),
                )
                val className = when {
                    dialerMain -> "example.dialer.MainActivity"
                    disposition == TargetDisposition.NECESSARY_CALL_UI -> "example.InCallActivity"
                    disposition == TargetDisposition.RECOVERY_SETTINGS_UI -> "example.AccessibilitySettingsActivity"
                    else -> "example.MainActivity"
                }
                val state = FocusModeState(FocusModeType.DEEP_FOCUS, status, 1_000L, 61_000L)
                val old = DeepFocusEnforcementPolicy.evaluate(state, f.PACKAGE, className, essentials,
                    keyguardLocked = disposition == TargetDisposition.LOCK_SCREEN,
                    overlayPermissionGranted = true, nowMillis = now)
                val base = f.context()
                val context = base.copy(daily = f.daily(0L), session = null, time = EvaluationTime(now, 1_000L),
                    target = base.target.copy(className = className, disposition = disposition),
                    focus = FocusRuntimeSnapshot.DeepFocus(1_000L, 61_000L, status))
                add(Case("deep $name", context, if (old == DeepFocusDisposition.BLOCK) RuleKind.DEEP_FOCUS else null))
            }
            TargetDisposition.entries.filter { it != TargetDisposition.UNCLASSIFIED }.forEach { addDeep(it.name, it) }
            addDeep("before", TargetDisposition.ORDINARY_APP, now = 999L)
            addDeep("end", TargetDisposition.ORDINARY_APP, now = 61_000L)
            addDeep("completed", TargetDisposition.ORDINARY_APP, status = FocusModeStatus.COMPLETED)
            addDeep("dialer is not blanket allowed", TargetDisposition.ORDINARY_APP, dialerMain = true)
        }

        private fun fromFriction(state: SessionFrictionState): SessionRuntimeSnapshot = SessionRuntimeSnapshot(
            state.packageName,
            SessionEpisodeSnapshot(state.episodeUsageSeconds, state.firstReminderSeconds, state.reminderCount,
                state.nextReminderAtSeconds, state.lastEngagementTimeMillis, state.pendingGate?.let {
                    PendingSessionGateSnapshot(it.id, it.phase, it.endTimeMillis, it.reminderCommitted)
                }),
            physicalSession = PhysicalSessionSnapshot(state.episodeUsageSeconds, true),
            admission = SessionAdmissionSnapshot(state.reentryGateArmed, state.bypassExpiresAtMillis, state.allowedForeground),
        )

        private fun sessionContext(state: SessionFrictionState, now: Long = 1_000L): RuleEvaluationContext =
            f.context().copy(daily = f.daily(0L), session = fromFriction(state), time = EvaluationTime(10_000L, now))

        private fun sessionCases(): List<Case> = buildList {
            // Use the existing transition owner to generate all six thresholds, not a new schedule.
            for (first in listOf(600L, 1_200L)) for (stage in 0..5) for (offset in -1L..1L) {
                val old = SessionFrictionManager(clock = { 1_000L })
                old.beginPhysicalSession(f.PACKAGE, first)
                repeat(stage) {
                    old.onActualUsage(f.PACKAGE, old.snapshot(f.PACKAGE)!!.nextReminderAtSeconds)
                    val gate = old.beginGate(f.PACKAGE, SessionGatePhase.REMINDER)!!
                    old.onReminderShown(f.PACKAGE, gate.id)
                    old.closeGate(f.PACKAGE, gate.id)
                }
                old.onActualUsage(f.PACKAGE, old.snapshot(f.PACKAGE)!!.nextReminderAtSeconds + offset)
                add(Case("session first=$first stage=$stage offset=$offset", sessionContext(old.snapshot(f.PACKAGE)!!),
                    if (old.isReminderDue(f.PACKAGE)) RuleKind.SESSION else null))
            }
            for (phase in SessionGatePhase.entries) for (afterCooldown in listOf(false, true)) {
                var now = 1_000L
                val old = SessionFrictionManager(clock = { now })
                old.beginPhysicalSession(f.PACKAGE, 600L)
                old.onActualUsage(f.PACKAGE, 600L)
                if (phase == SessionGatePhase.REENTRY) {
                    val reminder = old.beginGate(f.PACKAGE, SessionGatePhase.REMINDER)!!
                    old.onReminderShown(f.PACKAGE, reminder.id)
                    old.closeGate(f.PACKAGE, reminder.id)
                }
                old.beginGate(f.PACKAGE, phase)
                if (afterCooldown) now = 6_000L
                add(Case("pending $phase afterCooldown=$afterCooldown", sessionContext(old.snapshot(f.PACKAGE)!!, now),
                    if (old.isReminderDue(f.PACKAGE)) RuleKind.SESSION else null))
            }
        }

        private fun entryCases(): List<Case> = buildList {
            // Match the existing entry order: consumeBypass || isEntryAdmitted, then armed gate.
            for (mode in listOf("armed", "token", "expired", "admitted", "left")) {
                var now = 1_000L
                val old = SessionFrictionManager(clock = { now })
                old.beginPhysicalSession(f.PACKAGE, 600L)
                old.onActualUsage(f.PACKAGE, 600L)
                val gate = old.beginGate(f.PACKAGE, SessionGatePhase.REMINDER)!!
                old.onReminderShown(f.PACKAGE, gate.id)
                if (mode == "armed") old.closeGate(f.PACKAGE, gate.id) else {
                    now = 6_000L
                    check(old.allowNextEntry(f.PACKAGE, gate.id))
                    if (mode == "expired") now = 9_000L
                    if (mode == "admitted" || mode == "left") old.consumeBypass(f.PACKAGE)
                    if (mode == "left") old.onForegroundLeft(f.PACKAGE)
                }
                val context = sessionContext(old.snapshot(f.PACKAGE)!!, now).copy(event = EvaluationEvent.APP_ENTERED)
                val admitted = old.consumeBypass(f.PACKAGE) || old.isEntryAdmitted(f.PACKAGE)
                val expected = !admitted && old.isReentryGateArmed(f.PACKAGE)
                add(Case("entry $mode", context, if (expected) RuleKind.SESSION else null,
                    if (expected) SessionCandidateReason.REENTRY_GATE_ELIGIBLE else null))
            }
        }

        private fun priorityCases(): List<Case> = buildList {
            for (daily in listOf(false, true)) for (session in listOf(false, true)) {
                val old = selectInterventionType(daily, session, overlayShowing = false)
                val expected = when (old) {
                    InterventionType.DAILY_LIMIT_REACHED -> RuleKind.DAILY
                    InterventionType.SESSION_REMINDER -> RuleKind.SESSION
                    else -> null
                }
                add(Case("legacy priority daily=$daily session=$session", f.context().copy(
                    daily = f.daily(if (daily) 3_600L else 0L), session = if (session) f.session() else null), expected))
            }
        }
    }
}
