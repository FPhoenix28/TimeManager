# Current implementation

Architecture reviewed against source on 2026-10-14; build, validation and local deployment status updated on 2026-10-17. This document replaces the retired `tasks/plan.md` / `tasks/todo.md` as the current-state entry point. Historical plans and test counts are not current product guarantees.

Build-variant isolation was additionally implemented and verified on 2026-10-15; this did not change the remaining product rules below.

## Development model

The project remains in continuous development, not an automatic release cycle. Working changes are verified in TimeManager Dev; `master` is the intended user-accepted source baseline, while the installed TimeManager stable app follows the owner's separate update decision. Testing, user acceptance, commit, push/master integration and stable APK update are distinct gates. See [daily development workflow](DEVELOPMENT_WORKFLOW.md). The shared monitoring master switch is not implemented yet; dual-instance isolation follows DEVICE_SAFETY.md and must not default to force-stopping stable. Latest bounded Dev regression passed; full coverage and the owner's daily-app acceptance are not implied.

Repository prepared for public open-source release. The migration report records the independent `FPhoenix28/TimeManager` candidate as Private at that review; current remote visibility is not checked by this local preparation. The old private archive is separate and must not be published. Migration changed documentation assets/history, not product behavior. See [final migration report](FINAL_PUBLIC_MIGRATION_REPORT.md).

## Product

TimeManager is a personal Android digital-wellbeing application built around **intentional friction**, not an unbreakable app lock. Users select managed apps, choose limits, encounter deliberate pauses and can extend daily allowance. Missing capabilities or failures generally degrade open rather than trap a user.

## Usage day

`UsageDayProvider` owns the device-local **04:00 → next 04:00** boundary. Before 04:00, the date key is the previous calendar date. Daily usage, extension state and daily reminder receipts follow this key. Physical foreground sessions and reminder episodes do not reset just because the daily boundary changes.

## Managed apps and daily limits

`TrackedAppRepository` is the entry condition; `LimitRepository` stores per-package user limits. Settings live in Preferences DataStore. Base quota is the saved user value; today's extension is separate, cycle-scoped state; effective quota is their sum. No quota is synthesized from the old 1-minute test value when configuration is missing.

`UsageTimeRepository` reconciles system event-derived usage and a persisted realtime estimate using the larger cumulative value, not addition of overlapping sources. `AppTimer` supplies real foreground elapsed time. Quota evaluation and 80%/100% notification deduplication use the current effective quota and usage day. A daily overlay can appear on crossing the effective limit or entering while already over it. Extensions are currently **unlimited +15 minutes** each; they do not change the base setting and reset at the next 04:00 boundary.

Removing management uses `ManagedAppRepository`: untrack, delete quota, daily intervention/reminder and session-setting configuration. The running service observes management changes. Usage history is retained.

## Session reminder and friction

- `SessionSettingsRepository` defaults to **10 minutes only when a stored setting is absent**. Explicit existing values, including 15 minutes, remain user values. Editing supports 5–180 minutes.
- `SessionTracker` tracks physical foreground sessions. `SessionFrictionManager` alone owns reminder-episode usage, reminder count, next threshold, gate and admission state.
- Reminders occur at the configured first threshold, then **+8, +8, +5, +5… minutes** of actual engagement. A stage advances only after its reminder was successfully shown, not when Daily/Focus took priority.
- With connected Accessibility, the reminder sends HOME and shows a five-second pause. The following re-entry gate is armed once per stage and consumed when created. Continue creates a package-specific one-shot token (initial expiry: three seconds); an observed valid entry consumes it. Same-window events do not create another entry. Without Accessibility, the reminder stays in the same overlay after five seconds and offers Continue / Exit. Continue dismisses without relaunch or admission; Exit uses a user-requested HOME intent. A fallback stage commits progression without arming a gate, including after Accessibility reconnects.
- Quick departures do not erase the episode. Reset requires the target to be out of real foreground and **five minutes since last engagement**. Actual usage, launch attempts and successful continuation refresh engagement. A new physical session alone does not imply a new reminder episode.
- Episode state is process memory; it is not durable across process death. Session actions do not change daily quota or extensions.

In-place Session/Daily overlays flush and end physical timing after successful display. Timing stays paused while the overlay owns the decision; the next foreground tick after dismissal starts a new physical session while retaining the episode. Same-package restarts reset the repository's physical delta baseline. The monitoring process resumes the flushed estimate rather than reseeding that paused interval from system foreground time. Historical UsageStats/backfill semantics are not rewritten by this change.

## Intervention priority

**DEEP_FOCUS > FOCUS_BLOCK > DAILY_LIMIT > SESSION** (reminder / re-entry).

The process-scoped `InterventionCoordinator` shares snapshots and lifecycle-collected messages, not long-lived Service references. Daily calculations remain in the monitoring/evaluator path; Accessibility requests a fresh entry check rather than inventing another quota calculation. Overlay hosts own their Lifecycle, SavedState and ViewModelStore owners and dispose independently of monitoring.

## Todo

Room stores todos and reminder receipts. Migration 1→2 adds tables without replacing usage history. Unfinished old tasks remain original records and appear in the previous-day group; current-day completion follows the 04:00 usage day. Home shows a compact preview; Session/Daily overlays consume a read-only in-memory summary.

`TodoRepository`, `TodoRuntime` and `TodoSummaryProvider` expose shared state. `TodoReminderScheduler` uses WorkManager for 14:00 / 20:00 / 23:00 stages plus boundary rescheduling. Workers inspect the current unfinished snapshot, skip stale stages and deduplicate by usage-day/stage receipt. These are deferrable reminders, not exact alarms; realtime limit evaluation does not use WorkManager polling.

## Focus

Focus Mode persists selected blocked packages and an end time. Accessibility enforces selected-app entry and coordinates the existing small focus overlay. Duration is 1–720 minutes. Missing enforcement permissions produce guidance rather than a false claim of protection.

## Deep Focus

Deep Focus persists mode, status and timing. The lifecycle is **ACTIVE → COMPLETED → OFF**. Ordinary-app enforcement stops at expiration, independent of whether the user dismisses the completion screen. The full-screen overlay has an early-end flow with a hold and confirmation.

Necessary system exceptions include lockscreen, launcher, input method, System UI, selected recovery settings and recognized in-call/emergency activities. The default dialer package is not permanently blanket-allowed. Real incoming-call compatibility still needs device validation.

## Statistics

Room v2 uses one usage snapshot per `(packageName, usageDate)`; upserts keep the cumulative high-water mark, not sum repeated snapshots. Realtime estimates persist to DataStore at five-second increments, history at approximately 30-second increments and at existing flush points. App switches, service cleanup and day transitions attempt a final flush; abrupt process death can lose the most recent unflushed increment.

`UsageHistoryBackfillManager` queries available system usage events for missing ended dates plus today over a 30-day window, with a 15-minute throttle. Unavailable data is not proof of zero usage. Android retention/permission limits can prevent recovery. History survives removal from management; overview scope uses the currently managed set. Seven-/30-day views fill absent dates for display; a zero bar is not a guarantee that the OS retained complete evidence. Thirty-day previous-period comparison requires older records accumulated in Room because automatic backfill covers only 30 days.

## Permission health

- Usage Access: event-based usage and foreground detection.
- Overlay: cold-pause/focus windows over other apps.
- Notifications: ongoing monitoring and reminders.
- Accessibility: stable app-entry events, HOME, re-entry and Focus/Deep enforcement.

`PermissionHealthManager` reuses existing checkers and refreshes on lifecycle, runtime connection changes and a five-second monitoring health check. Enabled system settings and actual runtime connection are separate: DISABLED / ENABLED_BUT_DISCONNECTED / CONNECTED. The permission page shows these facts and folded OriginOS background advice; it does not reopen settings automatically.

Home shows a small badge and deduplicated notice. Onboarding requires the existing Usage/Overlay/Notification minimum; Accessibility is an enhancement. **Usage + Overlay support soft Session Reminder choices and Daily Limit overlays, including polling-based re-entry while over quota.** Connected Accessibility additionally provides forced HOME, exact re-entry gating and Focus/Deep enforcement. Focus/Deep startup uses actual availability, not merely an enabled switch.

Accessibility loss preserves ordinary Session/Daily pages and reminder progression, removes enhanced admission/gates, and affects future presentation rather than clearing the episode. Usage/Overlay loss dismisses relevant overlays; Usage loss ends timing, while Overlay loss still permits usage tracking and notifications. `UsageMonitorService` explicitly declares stopWithTask=false; that is not a guarantee against vendor background cleanup or system force-stop. See [fallback validation](testing/ACCESSIBILITY_FALLBACK_REPORT.md).

## Architecture

Public v0.9.0 preparation is on `release/v0.9.0`, starting from remote master `99ca0ce`. Release is `com.fphoenix28.timemanager` / **TimeManager** / **0.9.0**, Debug is `com.fphoenix28.timemanager.debug` / **TimeManager Dev** / **0.9.0-dev**; both use versionCode 3. Namespace and Kotlin class paths remain `com.example.timemanager`. Runtime own-app comparisons use Context/BuildConfig, while MainActivity recognition uses its retained class name, not applicationId plus a suffix.

Data, permissions, services, generated provider authorities and permissions are isolated by applicationId. Dev is the default device-testing identity, not a claim of installation. Release signing reads optional ignored local `keystore.properties`: all four keys are required when the file exists; absent means unsigned build check. Only `keystore.properties.example` is shareable. No public keystore or passwords are created in this preparation, and no phone operation or old-data migration is included. New identities are separate from the former development packages.

Historical local update on 2026-10-16 used 1.0.1/code 2 (`1.0.1-dev`) and the former `com.example.timemanager` identity. The locally signed compatibility APK was installed using `install -r` with ADB Success; no post-install UI/data/service check was run. Those historical facts and [build-variant report](testing/DEBUG_RELEASE_SPLIT_REPORT.md) remain unchanged. That local debug signing identity is not the future public maintainer signing key.

Key modules: `UsageMonitorService`, `TimeManagerAccessibilityService`, `InterventionCoordinator`, `SessionFrictionManager`, `UsageTimeRepository`, `TodoRepository`, `PermissionHealthManager`, Room, DataStore and WorkManager. Compose pages observe ViewModels. MainActivity uses the existing saved page/return-source state rather than Navigation Compose. See [architecture overview](architecture/OVERVIEW.md).

## Testing

The new public identity changes build/runtime inputs, so the previous 155-test result is not the validation for v0.9.0. Fresh **159 tests / 30 suites passed**, both builds succeeded, and static APK/authority checks passed during [v0.9.0 preparation](testing/2026-10-17-v0.9.0-release-preparation.md). On 2026-10-18 the owner confirmed successful formal signing (apksigner PASS, v2 true, one signer), new-identity installation, **Release smoke test PASS and owner acceptance PASS**. No runtime-affecting source change followed the 159-test run. See the [signed RC acceptance update](testing/2026-10-17-v0.9.0-signed-rc.md); its earlier signing failure remains historical evidence. Release freeze authorizes a local release-branch commit followed by a rebuild and final APK verification, not push, master merge, tag or publication. Historical reports before v0.9.0 retain their date-specific identities/results.

Latest 2026-10-17: the owner accepted the centered/no-intermediate-frame Session choice and follow-up HOME confirmation fix. Session HOME now waits for an observed launcher rather than treating action acknowledgement as an actual exit; its desktop button requests navigation regardless of presentation. **155 JVM tests / 29 suites passed**, Debug build succeeded; this round's functional device acceptance was performed by the owner, not Codex. The authorized daily update rebuilt Release successfully and installed the accepted source with `install -r` (ADB Success), retaining version 1.0.1/code 2 and the existing compatible local signing identity. No post-install test/data/service check was run by request; Dev was unchanged. The owner subsequently authorized local commits: accepted code/tests are in `415cc5b`, with documentation recorded separately. No master integration or push was performed. See [accepted stable update / Git handoff](testing/2026-10-17-stable-update-git-handoff.md). Broader validation remains open.

Latest 2026-10-16 [Dev follow-up](testing/2026-10-16-dev-timing-followup.md): bounded realtime timing, Accessibility-off Session wait/Continue, Daily crossing/re-entry and +15 quota passed with temporary background allowance. A minimal configured-service matcher separates saved permission from connection state; 147 JVM tests and Debug/Release builds passed. After reconnection, screenshot-only connected HOME/re-entry Gate/Continue and no same-stage repeat passed; hierarchy capture/debugger inspection had interfered with earlier runtime observations. Normal Dev was installed successfully, original battery/Accessibility settings restored, test management removed and Dev monitoring stopped. No stable APK/configuration change or user acceptance is implied; full vendor/long-duration matrices remain open.

Earlier Dev run on 2026-10-16 (historical, superseded by the bounded follow-up above): **PARTIAL / independent timing acceptance NOT PASSED**. Dev authorization, named notification coexistence, managed-app/quota save and update persistence passed. Vendor freeze was observed; after removing the freeze, foreground state still failed to follow the real target. Remaining cause was not proven in that run. Normal Dev parameters and original battery policy were restored, the test management item removed, and production monitoring restarted after an explicitly authorized temporary stop. No production APK/configuration was replaced in that run. See [historical runtime report](testing/DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md).

Build-variant isolation validation on 2026-10-15: **28 suites / 144 Debug JVM tests, zero failures/errors/skips**; Debug and unsigned Release builds passed. SDK inspection confirmed distinct IDs, labels, Startup authorities and generated signature permissions; Release has no testOnly/debuggable flag or fast regression fixture. Follow-up vivo device verification installed/started Dev only, confirmed both packages, independent UIDs/data directories, fresh onboarding, app-info name/version and no inherited Usage/Overlay/Notification grants. After reconnecting, both Accessibility labels, Dev's service description, launcher labels and Dev icon launch were verified. Vivo visually truncates long launcher names. Production installation metadata remained unchanged; no permissions were toggled. Enabled dual-instance runtime tests remain unverified. See [split report](testing/DEBUG_RELEASE_SPLIT_REPORT.md).

Accessibility fallback host regression on 2026-10-14 (working branch, not committed): **28 suites / 141 tests, zero failures/errors/skips**, Debug/unsigned Release builds, Todo migration and whitespace checks passed. ADB listed no connected device, so this change has no new device acceptance evidence. See [fallback implementation report](testing/ACCESSIBILITY_FALLBACK_REPORT.md).

Latest host validation on 2026-10-14: **27 suites / 125 Debug JVM tests, zero failures/errors/skips**, Debug/Release builds and todo migration check, repeated successfully from a fresh clone of the NEW independent repository with build/configuration caching disabled (3m 5s, all 96 tasks executed). This migration performed no device test. Latest earlier device evidence covers selected dark-mode portrait UI and navigation on vivo X200s / Android 16. Older reports retain their own date-specific counts and limitations. Full scope and pending owner publication review are in [readiness report](OPEN_SOURCE_READINESS_REPORT.md).

## Known limitations

- Vendor background restrictions and Accessibility event quality affect monitoring/enforcement; not a tamper-proof blocker.
- Complete Light/large-font/landscape, permission-revocation, incoming-call and cross-vendor matrices remain incomplete.
- WorkManager may deliver late; force-stopped apps need reopening before normal scheduling resumes.
- Historical system retention and previously unrecorded days can leave gaps; no guarantee of complete previous-30-day comparison.
- Episode state is not persisted; OS-managed backup remains enabled; diagnostic logs need privacy review before sharing.
- Without a complete local signing configuration, Release remains unsigned. The owner has now supplied the formal local key and accepted the signed v0.9.0 smoke test; signing secrets remain ignored. The historical installed 1.0.1 used a separate local debug identity for compatibility. Store permission/policy approval and broader device coverage are not implied by the accepted smoke test.

Actionable follow-up is in [OPEN_ISSUES](OPEN_ISSUES.md). No new features or business-rule changes are part of repository cleanup.
