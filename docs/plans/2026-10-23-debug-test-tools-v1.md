# Debug Test Tools v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Those named sub-skills are unavailable here; execution fallback is explicitly recorded below.

**Goal:** Debug-only, persistent Normal/Fast/Custom timing controls, exercising unchanged real usage and intervention paths.

**Architecture:** Extend the existing TimeConfig with progression durations and inject a live TimeConfigProvider at existing consumers. Debug owns settings/controller/SharedPreferences/launcher Activity; Release owns a fixed production provider. Existing episode state remains in SessionFrictionManager only.

**Tech Stack:** Kotlin, Compose Material3, StateFlow, Debug SharedPreferences, JUnit4.

**Spec:** Owner's Debug Test Tools v1 request (2026-10-23); requirements and timing source map below are the implementation contract.

## Global constraints

- Preserve current branch/worktree and all pre-existing Phase1–4.2 work. No commit/push/merge/tag.
- Phase4.2 device gate and Phase4.3 paused. No ADB, installation or device test.
- No direct state-mutation controls, base-quota override, business DataStore changes, RulesEvaluator/arbitration/Accessibility changes, or signing operation.
- Test Mode OFF and Release use production fields exactly: notifications 900/1800s, extension 900s, gate 5s, idle 300s, bypass 3s, no first-threshold override, progression 480/480/300s.
- Fast: first 20s, progression 15/15/10s, idle 30s, gate 2s, extension 30s, notifications 15/30s. Existing fastRegression fixture expectations remain unchanged.
- Custom first 5–600s, positive progression, idle positive, gate 1–5s, extension 10–900s, notification thresholds positive/increasing. UI fields explicitly in seconds.
- Debug settings persist separately from user business preferences; Reset removes these settings and returns production immediately.
- Existing shown overlays retain their request/deadline/extension amount, so displayed action equals granted amount. New requests use new config. Existing extension totals are never rewritten.
- First threshold updates existing episodes only before first reminder, matching existing semantics. Committed next thresholds/history stay unchanged; future successful stage commits use current progression. Idle uses existing predicate, with current duration. No episode reset on setting change.

## CURRENT_TIMING_SOURCE_MAP

| Source/consumer | Before | Planned parameter boundary |
| --- | --- | --- |
| debug/release config/BuildTimeConfig.kt | fixed value; Debug has injection-only fastRegression | common provider API, Debug controller versus fixed Release |
| config/TimeConfig.kt | notifications/extension/gate/idle/bypass/first override | add three progression durations with production defaults |
| config/InterventionRuntime.kt | captures fixed gate/idle/bypass once | give existing friction manager live provider |
| monitor/SessionFrictionManager.kt | fixed durations; +480/+480/+300 literal | read duration parameters at existing gate/idle/bypass/stage operations; no state transition rewrite |
| monitor/SessionTracker.kt | constructor-fixed notification thresholds | optional provider read on existing update; receipts preserved |
| service/UsageMonitorService.kt | stored config and constructor snapshots; first override | getter/provider at parameter sites only; change-only Debug timing log via variant implementation |
| domain/limit/LimitInterventionManager.kt | fixed extension snapshot | provider for future request/increment, optional shown-request amount for click consistency |
| notifications/NotificationHelper.kt | fixed notification text thresholds | provider snapshot when composing reminder text |
| ui/intervention/LimitReachedScreen.kt | extension text from request | unchanged; already reflects seconds |
| TimeManagerApplication.kt | no timing init | initialize variant provider before runtime consumers |

## Ordered implementation / verification

### 1. Provider and isolated Debug settings

Files: TimeConfig.kt; new TimeConfigProvider.kt; debug/release BuildTimeConfig.kt; new DebugTimingSettings.kt, DebugTimingPreferences.kt; TimeManagerApplication.kt; new testDebug DebugTimingSettingsTest.kt; new testRelease ReleaseTimeConfigTest.kt.

- [x] Add failing tests for defaults, presets, custom bounds, persistence and reset.
- [x] Add `fun interface TimeConfigProvider { fun current(): TimeConfig }` and `TimeConfig.production()`.
- [x] Implement pure `DebugTimingController(storage)` with `settings: StateFlow<DebugTimingSettings>`, `setMode`, `saveCustom`, `reset`, `current`; storage only reads/writes debug timing settings.
- [x] Implement Debug preferences adapter; initialize variant once from Application. Release initialization is no-op and provider always production.

Example acceptance tests:
```kotlin
val controller = DebugTimingController(MemoryStorage())
assertEquals(TimeConfig.production(), controller.current())
controller.setMode(DebugTimingMode.FAST)
assertEquals(20L, controller.current().sessionInterventionOverrideSeconds)
controller.reset()
assertEquals(TimeConfig.production(), controller.current())
```

Verify: targeted Debug settings tests, Release provider test; old tests unchanged.

### 2. Live parameter consumption without new business state

Files: SessionFrictionManager.kt, SessionTracker.kt, InterventionRuntime.kt, UsageMonitorService.kt, LimitInterventionManager.kt, NotificationHelper.kt; new testDebug RuntimeTimingTest.kt.

- [x] Add tests running the actual friction manager at 20/35/50/60/70s, dynamic first-threshold/stage preservation, captured cooldown, shortened idle predicate and notification receipts.
- [x] Replace literal/snapshotted duration reads with optional injected provider; preserve constructor injection used by all old tests.
- [x] Pass provider from current runtime, not another manager instance. Do not touch Engine/arbitration/entry paths.
- [x] Freeze shown Daily request increment through click; changing config affects future requests, never already persisted extension.

Example stage acceptance:
```kotlin
for (at in listOf(20L, 35L, 50L, 60L, 70L)) {
    manager.onActualUsage("app", at)
    assertTrue(manager.isReminderDue("app"))
    val gate = manager.beginGate("app", SessionGatePhase.REMINDER)!!
    manager.onReminderShown("app", gate.id)
    manager.closeGate("app", gate.id)
}
assertEquals(5, manager.snapshot("app")!!.reminderCount)
```

Verify: targeted new tests then all Debug JVM tests, preserving 448 existing tests/expectations.

### 3. Debug-only launcher and UI

Files: src/debug/AndroidManifest.xml; src/debug/.../debugtools/DebugTestToolsActivity.kt; Debug resources if needed.

- [x] Add exported MAIN/LAUNCHER Activity named TimeManager Test Tools in debug manifest only.
- [x] Compose screen: Use test timing switch, Normal/Fast/Custom presets, explicit seconds inputs, separated notification fields, active effective-config summary/banner, visible Reset to Production Timing.
- [x] Validate custom input before saving; no UI action mutates business state. Pending edits are temporary UI state until Apply.
- [x] Reuse existing Material theme; leave main production UI and main manifest unchanged.

Verify: assembleDebug and SDK manifest inspection for the second launcher; compileReleaseKotlin + merged Release manifest/classes lack Debug Activity/settings/storage. No signing/assembleRelease/device action.

### 4. Documentation / final host checks

Files: docs/testing/2026-10-23-debug-test-tools-v1.md; this checklist.

- [x] Document all source/parameter mappings, isolation, storage, dynamic semantics and Reset.
- [x] Provide simple honest manual recipes: high Daily + Fast for Session; Custom first=60s with Daily=1min and a fresh usage baseline for simultaneous thresholds (existing usage and episode history may shift alignment); Fast +30s Daily extension. Fast alone does not guarantee simultaneous due.
- [x] Record exact Debug total tests/errors/skips, Debug build, Release compile/provider tests, manifest/bytecode isolation; do not claim device gates passed.
- [x] `git diff --check`, inspect task diff versus pre-existing worktree; no commit/push/device action.

## Execution

Execute sequentially in the current session. The skill's named execution sub-skills are not installed; use the checklist and focused tests directly. Project `docs/plans/` overrides the generic skill locations, and owner restrictions override generic commit/handoff examples. No subagents needed.
