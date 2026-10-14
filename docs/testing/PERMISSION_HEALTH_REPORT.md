# Permission health verification — 2026-10-13/14

Device: vivo X200s / Android 16. Package: `com.example.timemanager`. Private identifiers, actual managed-app lists, raw screenshots and usage totals are omitted.

## Implementation and host evidence

PermissionHealthManager/State/NoticeTracker reuse existing Usage, Overlay and Accessibility checkers; notification checks cover runtime consent and the master notification switch. Refresh occurs on startup/resume/capability changes, not every timer tick. Home uses a small badge and deduplicated short notice, linking to the existing permission screen; onboarding minimums stay unchanged.

The old ordinary Session entry was silently dropped when Accessibility was absent. The regression failed first; capability separation fixed it: **OVERLAY_ONLY** is available for the ordinary reminder, while HOME, re-entry and Focus/Deep enforcement still require Accessibility. Stage progression, quota and database algorithms were unchanged.

At that stage **121** Debug JVM tests passed with zero failures/errors; Debug/Release builds and whitespace checks succeeded. No connected instrumentation was run.

## Recorded device results

- Missing-capability badge and short notice appeared; no blocking startup modal or large warning card.
- Existing permission screen showed real state and explained the effect of missing enhanced capabilities.
- With Accessibility off, a temporary 20-second first threshold produced an ordinary five-second overlay over the target app, without HOME.
- Focus/Deep entry showed real missing-permission guidance instead of claiming enforcement had started.
- After enabling the app's service, permission status refreshed and the badge disappeared. Enhanced HOME/re-entry/Continue worked; same-stage return did not re-gate.
- A captured enhanced overlay was a **re-entry gate**, not the first Reminder; it is not used as proof of a complete first-reminder HOME recording.
- Revocation produced one short notice; repeated resume with unchanged state did not repeat it.
- Normal-parameter update succeeded; ongoing monitoring/ID 1001 remained active and a short observation beyond 20 seconds showed no temporary reminder.

## Diagnostics, privacy and limitations

ADB was intermittently offline despite the user confirming a connected cable. Host transport was recovered; phone USB settings were not reset. Vendor Debug-log restrictions limited the full event trail. A temporary app-private probe was used, then removed from source and the exact test file cleaned; it did not read page content or replace configuration. Only explicit test fixtures retain shortened timings.

The full Usage/Overlay/Notification revocation matrix, revocation during an overlay, complete first-reminder HOME capture and the notice's View action were not all performed on-device. Initial Accessibility/display settings were restored. No other service settings, existing tasks, quotas or history were cleared; no uninstall, helper installation or unsafe cleanup occurred.
