# Current implementation

Reviewed against source on 2026-10-14. This document replaces the retired `tasks/plan.md` / `tasks/todo.md` as the current-state entry point. Historical plans and test counts are not current product guarantees.

Repository prepared for public open-source release. The independent `FPhoenix28/TimeManager` candidate remains Private pending owner review; the old private archive is separate and must not be published. Migration changed documentation assets/history, not product behavior. See [final migration report](FINAL_PUBLIC_MIGRATION_REPORT.md).

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
- With Accessibility, the reminder sends HOME and shows a five-second pause. The following re-entry gate is armed once per stage and consumed when created. Continue creates a package-specific one-shot token (initial expiry: three seconds); an observed valid entry consumes it. Same-window events do not create another entry. Without Accessibility, the ordinary reminder can show overlay-only.
- Quick departures do not erase the episode. Reset requires the target to be out of real foreground and **five minutes since last engagement**. Actual usage, launch attempts and successful continuation refresh engagement. A new physical session alone does not imply a new reminder episode.
- Episode state is process memory; it is not durable across process death. Session actions do not change daily quota or extensions.

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

`PermissionHealthManager` reuses existing checkers and refreshes on lifecycle and capability changes. Home shows a small badge and deduplicated notice. Onboarding requires the existing Usage/Overlay/Notification minimum; Accessibility is an enhancement. **Accessibility OFF allows an ordinary Session Reminder overlay-only fallback, but HOME / Re-entry / Focus / Deep enforcement are incomplete.**

## Architecture

Key modules: `UsageMonitorService`, `TimeManagerAccessibilityService`, `InterventionCoordinator`, `SessionFrictionManager`, `UsageTimeRepository`, `TodoRepository`, `PermissionHealthManager`, Room, DataStore and WorkManager. Compose pages observe ViewModels. MainActivity uses the existing saved page/return-source state rather than Navigation Compose. See [architecture overview](architecture/OVERVIEW.md).

## Testing

Latest host validation on 2026-10-14: **27 suites / 125 Debug JVM tests, zero failures/errors/skips**, Debug/Release builds and todo migration check, repeated successfully from a fresh clone of the NEW independent repository with build/configuration caching disabled (3m 5s, all 96 tasks executed). This migration performed no device test. Latest earlier device evidence covers selected dark-mode portrait UI and navigation on vivo X200s / Android 16. Older reports retain their own date-specific counts and limitations. Full scope and pending owner publication review are in [readiness report](OPEN_SOURCE_READINESS_REPORT.md).

## Known limitations

- Vendor background restrictions and Accessibility event quality affect monitoring/enforcement; not a tamper-proof blocker.
- Complete Light/large-font/landscape, permission-revocation, incoming-call and cross-vendor matrices remain incomplete.
- WorkManager may deliver late; force-stopped apps need reopening before normal scheduling resumes.
- Historical system retention and previously unrecorded days can leave gaps; no guarantee of complete previous-30-day comparison.
- Episode state is not persisted; OS-managed backup remains enabled; diagnostic logs need privacy review before sharing.
- Release APK is unsigned, `versionName=1.0` is legacy metadata, and store permission/policy approval has not been established.

Actionable follow-up is in [OPEN_ISSUES](OPEN_ISSUES.md). No new features or business-rule changes are part of repository cleanup.
