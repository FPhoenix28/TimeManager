# Delayed Deep Focus Phase 3 Plan

**Goal:** A Session-only, Gate-protected action schedules approximately five minutes of preparation followed by the existing 30-minute Deep Focus, with cancellation.

**Architecture:** WorkManager unique one-time work owns pending state. A small scheduler serializes schedule/cancel/start and observes WorkInfo; the Worker rechecks permissions, expiry and mode, then uses FocusModeRepository.startDeepFocus without replacement. A single applied-work receipt is stored atomically with the Focus preference update for idempotence; FocusModeState gains no preparation state. Existing Accessibility state observation performs enforcement/notification/overlay display.

**Spec:** Owner's Phase 3 request in this chat. Phase 2 accepted and checkpoint committed as `e93f32b` on feat/todo-experience.

## Decisions and boundaries

- Initial delay: 5 minutes; Deep Focus duration: 30 minutes from actual start. More than 5 minutes late after the expected start: abandon, with a visible result in the existing setup page.
- KEEP preserves an existing pending request and reports that its original time remains unchanged.
- Confirmation stays inside the existing overlay window; do not open an Activity-owned Dialog from an application-context overlay.
- Successful scheduling reuses onContinue, including existing Daily revalidation and HOME-first/fallback admission semantics. No timing reset, quota extension or special exemption.
- Cancel entry: Home → 静心模式. It remains available without notification permission. Starting and cancelling serialize; cancellation that arrives after start reports that the existing early-end control must be used.
- No device operations, Release build, version changes, Phase 3 commit/push/merge/tag or Phase 4 work. No Rules Engine, Episode, Daily, 04:00, Todo database/reminder or ordinary Focus rule changes.

## Implementation / validation

- [x] Scheduler/Worker: unique KEEP, durable WorkInfo observation, explicit errors/results, cancellation, late-window rejection, permission recheck, no retries that might start unexpectedly later.
- [x] Repository: reuse the existing Deep start checks; atomically ignore an already-applied delayed request. Preserve manual start behavior and existing FocusState/UI.
- [x] Session UI/controller: third primary action, same countdown enablement, in-window confirmation/cancel/failure; successful scheduling ends through the original Continue callback. Daily has no new action.
- [x] Existing Deep setup/ViewModel: show pending work and cancel, plus unsuccessful automatic-start reasons.
- [x] Host WorkManager tests with injected clock/permissions, real Focus repository/preferences and WorkManager test driver. Cover schedule, unique KEEP, cancellation/race, 30-minute start, modes, permissions, late expiry, duplicate execution and reconstructed scheduler.
- [x] Compose tests: three gated actions, confirmation cancel/error/success, no todos, REENTRY/fallback, portrait/landscape/large font and Daily isolation. Run full Debug suite, assembleDebug and diff check; inspect host renders.

## Files

- `app/src/main/java/com/example/timemanager/reminders/DelayedDeepFocusScheduler.kt` (new): WorkManager scheduling/status/cancellation/start coordination.
- `app/src/main/java/com/example/timemanager/reminders/DelayedDeepFocusWorker.kt` (new): restored background execution, terminal failure reason, no retry.
- `app/src/main/java/com/example/timemanager/data/preferences/FocusModePreferences.kt`: one applied-request receipt alongside the existing atomic mode write.
- `app/src/main/java/com/example/timemanager/data/repository/FocusModeRepository.kt`: existing Deep start guard reused with optional request deduplication; manual starts retain their prior behavior.
- `app/src/main/java/com/example/timemanager/ui/intervention/SessionFrictionContent.kt`: Gate-protected third action, in-window confirmation and failure presentation; compact-height typography.
- `app/src/main/java/com/example/timemanager/ui/intervention/LimitReachedScreen.kt`: pass scheduling only to Session.
- `app/src/main/java/com/example/timemanager/ui/intervention/InterventionOverlayController.kt`: bind scheduler and report new/existing arrangement; existing Continue callback remains responsible for admission/Daily checks.
- `app/src/main/java/com/example/timemanager/ui/focus/DeepFocusViewModel.kt`: observe WorkInfo and cancel the selected pending work.
- `app/src/main/java/com/example/timemanager/ui/focus/DeepFocusSetupScreen.kt`: in-app cancel entry and dismissible failure notice. The active Deep screen and its Todo presentation are unchanged.
- `app/src/main/java/com/example/timemanager/MainActivity.kt`: inject the scheduler into the existing Deep ViewModel.
- `app/src/testDebug/java/com/example/timemanager/reminders/DelayedDeepFocusSchedulerTest.kt` (new): 13 host scheduling/repository integration tests.
- `app/src/testDebug/java/com/example/timemanager/ui/intervention/SessionTodoUiTest.kt`: 9 additional Compose tests, 22 in this class total.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`: Debug-host-only WorkManager testing dependency, matching the existing runtime version.
- This plan/validation record (new).

## Results

- `:app:testDebugUnitTest :app:assembleDebug --console=plain`: PASS. **622 tests / 65 suites, zero failures/errors/skips**. Scheduling tests: 13; Session Compose tests: 22. `git diff --check`: PASS.
- WorkManager tests use the test driver, a test application/database, injected clock/permission predicate and real Focus preferences/repository. Scheduler reconstruction recovers pending WorkInfo; the applied receipt prevents re-start after an early end. Cancelling before execution prevents start; a cancel that loses to the committed start reports that it is too late and preserves the current end time.
- Reviewed native host images under ignored `app/build/reports/todo-session-phase3/`, particularly `three-actions-light.png`, `three-actions-small-dark.png`, `confirmation-dark.png`, `confirmation-landscape-dark.png`, and `daily-unchanged-light.png`. Test fixture deadlines are not product timing overrides.
- Limitations: initial delay is a lower bound, not an exact alarm. Work starting more than five minutes after the expected time is abandoned. Clock changes that put execution outside that window also abandon it. Missing live Accessibility connection/Overlay permission or an active conflicting mode produces a failed work result and an in-app notice; no automatic retry, forced mode replacement or false “active” state.
- A Deep mode still present in COMPLETED status retains the existing requirement to finish/close it; this path does not silently replace its completion page.
- No device operation or Release build. Actual OS process reclamation, vendor background scheduling, real Overlay/HOME behavior and owner acceptance remain **not device-verified**. Reconstructing a scheduler in the host test is not a claim of a real process-death test.
- Phase 3 remains unstaged/uncommitted on feat/todo-experience; Phase 2 checkpoint is `e93f32b`. No push/merge/tag/version or Phase 4 work.
