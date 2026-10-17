# Session reminder alignment and direct choice transition

## Scope

- Date: 2026-10-17.
- Branch: `fix/accessibility-fallback-interventions`; starting commit: `b905d33`.
- Existing uncommitted accessibility fallback, build isolation and documentation work was preserved. This task does not commit or push it.
- Owner authorized code changes plus Dev cover-installation only. Runtime device testing and final visual acceptance are intentionally left to the owner.

## Changes

- `ResponsiveInterventionScaffold.kt`: information/portrait containers fill card width so their center alignment uses the full content area.
- `SessionFrictionContent.kt`: full-width centered heading, timer and explanatory text; stable minimum timer-region height; action buttons keep their space during the countdown and become enabled at its deadline. First reminder displays its final choice in the same composition, with no temporary "稍作停留" placeholder.
- `InterventionPresentation.kt`: both Session gate phases support explicit choices at the existing deadline; HOME-first Continue uses existing admission, while in-place Continue remains non-launching.
- `SessionFrictionManager.kt`: the first reminder can grant a deadline-validated one-shot admission. Its episode usage, progression and later re-entry gate are retained. Re-entry consumption is unchanged.
- `UsageMonitorService.kt`, `InterventionOverlayController.kt`, `LimitReachedScreen.kt`: remove the automatic countdown-dismiss callback plumbing. The Compose host and window attach/dispose implementation are unchanged.
- `InterventionPresentationPolicyTest.kt`, `SessionFrictionManagerTest.kt`: deadline/choice/admission and retained re-entry/progression regressions.

Daily quota/extension, UsageStats, timer increments, Room, 04:00 boundary, notification IDs and Accessibility detection are unchanged in this task. Normal time configuration is unchanged; no temporary overrides were introduced.

## Host validation

1. Before implementation: `:app:testDebugUnitTest --tests '*InterventionPresentationPolicyTest' --tests '*SessionFrictionManagerTest'` ran 34 tests with three expected failures: first-reminder choice unavailable, Continue not admitted, and first-reminder admission rejected.
2. After implementation: `:app:testDebugUnitTest :app:assembleDebug --console=plain` succeeded (18 seconds; 46 tasks, 10 executed and 36 up-to-date). Tests were executed, not merely reused.
3. XML results: 29 suites / 150 tests, zero failures, errors or skips.
4. `git diff --check`: PASS.
5. SDK `aapt2 dump badging`: package `com.example.timemanager.debug`, label `TimeManager Dev`, version `1.0.1-dev`, code 2.

These checks cover state policy and compilation, not rendered centering or frame-by-frame transitions. No device test runner was used; `connectedDebugAndroidTest` was not run.

## Installation

- Target verified through ADB: the development vivo X200s / model V2458A; existing Dev installation found.
- Command: `adb -s <verified-development-device> install -r -t app/build/outputs/apk/debug/app-debug.apk`.
- Initial installation: FAILED. ADB returned `INSTALL_FAILED_ABORTED: User rejected permissions`; the owner reported that no USB confirmation prompt appeared. This system error does not establish that the owner actively rejected installation. The device remained connected. No automatic retry or destructive fallback was attempted.
- Owner explicitly requested a new cover-install attempt. The same verified Dev APK was installed with the command above; the owner confirmed the USB prompt and ADB returned `Success`. At least 60 seconds elapsed from this attempt's start before completion. No further device command was run after installation success.
- Owner notified to confirm USB installation; at least one minute must elapse from installation start before any later device operation.
- No uninstall, clear-data, permission changes, force-stop, application launch, screenshots or UI interaction are part of this task. Stable TimeManager is not updated or paused.
- Data: replacement installation is data-preserving by design; actual business records are not read or compared because the owner requested installation only.
- Final installation result: SUCCESS. No application was launched for follow-up verification; device functionality and business data remain unverified by request.
- Dual-instance isolation: no functional test is run and no managed target is opened by Codex; existing stable/Dev monitoring is not deliberately changed.

## Owner acceptance / Git

- Owner acceptance: PENDING. Check first-reminder alignment, `1 second -> ready choice` without a transient screen, Continue/Stay Home and the unchanged second re-entry gate.
- Subsequent owner review confirmed centering/no-flash behavior but found a HOME navigation regression. The follow-up [HOME confirmation fix](2026-10-17-session-home-confirmation.md) supersedes navigation acceptance for this build; the owner accepted that updated source on 2026-10-17. The original 150-test result above did not verify physical HOME behavior.
- Device runtime test: NOT RUN by request; visual fix not claimed as device-verified.
- No new test settings or files require device cleanup.
- No stage, commit, push, merge, tag or stable APK update performed.
