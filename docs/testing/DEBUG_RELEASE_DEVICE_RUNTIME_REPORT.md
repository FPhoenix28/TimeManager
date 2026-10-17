# Dev runtime acceptance — 2026-10-16

## Verdict

**PARTIAL / runtime acceptance NOT PASSED.** Build/package isolation, authorization, notification naming and configuration persistence passed. Dev's independent foreground timing and Session/Daily intervention flow did not pass this run. No business-logic fix is claimed.

Device: the verified development vivo X200s, Android 16. Only `com.example.timemanager.debug` was installed/updated. Later, the owner explicitly authorized temporarily stopping production TimeManager to remove interference. Production was restarted at the end; its installed APK was not replaced.

## What actually ran

| Check | Result / evidence |
| --- | --- |
| Dev Usage Access grant | PASS; own system setting changed to allow, returning to onboarding automatically showed enabled |
| Dev notification grant | PASS; system dialog identified TimeManager Dev and authorization persisted |
| Dev overlay grant | PASS; own app's switch enabled and AppOps reported allow; onboarding refreshed automatically |
| Separate grants | PASS; these were Dev's own permissions, not copied from production |
| Onboarding completion | PASS; completed through UI and remained completed after process restart and package update |
| Application list / search | PASS after allowing OriginOS's separate installed-app-list access and restarting Dev; initial restricted list had shown only Dev |
| Managed selection / quota save | PASS; Dev UI selected 小红书, saved 180 minutes and read back the same package-specific value |
| Persistence across Dev update | PASS; the managed package, 180-minute quota and completed onboarding survived `install -r -t` |
| Foreground service / notification | PASS for existence and branding; Dev's service reported `isForeground=true`, ID 1001; notification shade displayed TimeManager Dev and TimeManager Dev 正在监控 |
| Notification coexistence | PASS; production and Dev ongoing notifications were present simultaneously under their respective names before isolation |
| Accessibility-off Session choices / pause / Continue / Exit | NOT PASSED / not independently established for Dev |
| Daily crossing / re-entry / +15 | NOT RUN after the independent timing path failed; no extension was granted in this run |
| Connected Accessibility / HOME / re-entry gate | NOT RUN; neither service switch was enabled |
| Focus / Deep / permission revocation matrix / 04:00 crossover | NOT RUN; outside the narrowed run after failure |

### Selection evidence

This run did **not** omit managed-app selection. Reads pinned to the Dev sandbox, both after saving and after installing the temporary test APK, returned:

```text
tracked_apps: tracked_package_names = ["com.xingin.xhs"]
app_limits: daily_limit_minutes:com.xingin.xhs = 180
onboarding: onboarding_completed = true
```

The empty list visible later was the deliberate end-of-test removal of this newly created management item, not the setup used during timing verification. The user's existing production managed list was not edited.

## Runtime findings and uncertainty

1. **Discarded false-positive overlay.** The first visible 20-second page belonged to production, not Dev: WindowManager identified the production owner UID/package on the application-overlay window. It must not be counted as a successful Dev reminder, countdown or timing-pause test. The owner manually dismissed that page and then explicitly allowed production to be stopped for testing. Production was force-stopped only under that authorization.
2. **Confirmed vendor freeze.** With production isolated, Dev still had a foreground service/notification but its persisted realtime value did not advance. `dumpsys activity service <Dev component>` explicitly reported that the Dev package had been frozen. Service presence alone was therefore not evidence of a running timer.
3. **Freeze removed, foreground state still stale.** Dev's own battery policy was temporarily changed from 智能控制后台耗电 to 允许后台耗电, then Dev restarted. Service dump became responsive instead of reporting frozen. A read-only debugger inspection confirmed the temporary time configuration was active, but the detector's stored `currentForegroundApp` still pointed to Dev while ActivityManager showed 小红书 in front. Persisted realtime usage still did not advance as expected. The remaining root cause is **NOT PROVEN**; a timer/foreground-events integration issue, event delivery and startup ordering have not been distinguished. No algorithm change was attempted during this acceptance run.
4. Logcat did not expose the desired application debug traces reliably on this device. A scoped Dev-only debugger attachment was used instead; it was detached, all suspended threads resumed, and the one temporary ADB forwarding port removed. No tracing helper APK or instrumentation runner was installed.

The confirmed freeze explains part of the stalled run, but is **not** sufficient to claim the remaining behavior is solved. Further testing needs a tight foreground-event/state reproduction on Dev; do not resume long blind waits or reuse a production overlay as evidence.

## Test configuration and restoration

- The temporary change lived only in `src/debug/.../BuildTimeConfig.kt`: explicit overrides enabled and first Session intervention shortened to 20 seconds. Notification thresholds, daily stored quota, +15-minute extension, idle reset and product algorithms were unchanged.
- A separate local temporary Dev APK was built and installed with `-r -t`; the owner confirmed installation. The source was immediately restored to normal and normal Debug/Release artifacts rebuilt while awaiting the phone's confirmation.
- Required host command succeeded in 2 seconds (mostly up-to-date tasks): `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`. Existing result XML remains 28 suites / 144 tests / zero failures/errors/skips; this cached host result is not a fresh device acceptance result.
- At the end, the normal-parameter **Dev APK was successfully installed with `-r -t`**. The owner confirmed the prompt, and the first subsequent phone operation was 86 seconds after installation began. `BuildTimeConfig.kt` has no remaining diff; normal configuration uses overrides=false and no Session intervention override.
- The newly created Dev managed item was removed through the existing UI. Readback confirmed `app_limits={}` and `tracked_package_names=[]`, both before and after normal APK restoration. No real base quota was overwritten with a test duration.
- Dev's battery policy was restored to its recorded original 智能控制后台耗电 selection (radio-state readback confirmed). Dev's normal first-run Usage/Overlay/Notification and installed-app-list authorizations were retained as announced; Accessibility remains off.
- Normal startup-generated Dev history/preferences were not cleared. Ordinary management removal preserves history under the existing product rule. No database rows, user directories or original application data were deleted.
- Production was reopened under the owner's temporary-stop authorization and its foreground monitoring service/notification ID 1001 was confirmed running again alongside Dev. The phone was left on production's normal MainActivity, not a test target app.
- Production code path, version, first-install time, last-update time and data-directory metadata still matched the pre-run snapshot. This confirms no production APK replacement, not a promise to freeze naturally changing usage counters.

## Boundaries

No uninstall, pm clear, destructive fallback, connected instrumentation, signature changes or production configuration/permission edits. No other app's content was clicked. Only the previously authorized target app was brought forward; its feed contents and private screenshots are not published here. No commit/push.

This run restores **Dev's** temporary test deployment. It does not restore or replace the earlier single-package production APK; production's own observed 20-second reminder remains a separate deployment concern requiring explicit production-update intent.
