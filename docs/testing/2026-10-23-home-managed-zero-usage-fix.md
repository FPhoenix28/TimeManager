# Managed apps missing from Home — narrow follow-up

Date: 2026-10-23. Branch: `feat/rules-engine`. Existing Rules Engine / Debug Test Tools worktree changes preserved; no commit or push.

## Reported symptoms / evidence

Owner reported that a newly managed app did not appear on Home, and that test timing parameters could not be changed. Owner clarified the Home symptom and supplied Home/Statistics screenshots. Home showed the no-managed-app empty state; Statistics still showed the managed test app with usage on the previous day and no bar for today. Private screenshots remain outside Git.

These are two separate concerns. The Home list defect has a host-side reproduction and fix below. The independent Test Tools access/edit/save symptom is **not yet classified or declared fixed**; clarification about its separate launcher screen was requested. No screenshot or failing tool-screen save trace was supplied in this follow-up.

## Confirmed Home cause

`UsageStatsReader` returns only packages with positive observed usage. `HomeViewModel.loadTodayUsage()` captured that list once and mapped only those rows, even while collecting tracked-package changes. `HomeScreen` then filtered on the presence of an allowance rather than using the managed set as membership authority.

Consequently a saved, installed managed app with no usage row today could not appear; new management changes could recompute the same incomplete usage list and still show the misleading no-management empty state. Previous-day Room records in Statistics did not supply the missing Home row. Read-only comparison with the release baseline confirmed this membership projection already existed before Debug Test Tools; no evidence of settings deletion or Debug timing overwriting managed configuration was found.

## Minimal change

- Added pure `ui/home/ManagedHomeUsage.kt`: take managed installed metadata as membership, look up any existing usage row by package, use zero when absent, preserve real usage and sort descending.
- `HomeViewModel` resolves only tracked packages with the existing `InstalledAppRepository`/shared metadata cache on its existing IO block, and uses that projection each time the tracked flow changes. No PackageManager work was added to Compose and no new UsageStats query loop was added.
- `MainActivity` passes the existing installed-app repository to Home's factory.
- `HomeScreen` no longer interprets missing allowance as not-managed; it displays the already-managed projection, using existing no-allowance presentation where necessary.
- No change to UsageStats, realtime timing, Daily/Session rules, Overlay, Debug timing settings, Room history, DataStore persistence or the 04:00 boundary.

## Reproduction and verification

Added `app/src/test/java/com/example/timemanager/ui/home/ManagedHomeUsageTest.kt` with four cases:

1. newly managed / no usage row -> shown with zero and installed display metadata;
2. managed membership changes while the usage snapshot remains unchanged -> new row appears;
3. existing durations preserved, zero row included, unmanaged usage excluded, descending order;
4. removed management disappears without modifying the historical input.

Before fixing the extracted legacy projection, the focused run completed **4 tests / 3 failures**, reproducing missing rows. After fixing and wiring the projection into Home, the full host run passed:

```text
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:testReleaseUnitTest --console=plain
```

- Debug: **472 tests / 53 suites / 0 failures/errors/skips**.
- Release: **450 tests / 50 suites / 0 failures/errors/skips**.
- Debug APK build: PASS.
- Release compile and host tests: PASS; no Release APK/signing task.
- `git diff --check`: PASS.
- SDK APK identity: Dev `com.fphoenix28.timemanager.debug`, TimeManager Dev, 0.9.0-dev/code 3; both main and Test Tools launcher entries remain.
- Existing Debug tools tests/production expectations unchanged. This is a pure projection regression, not a claimed Android UI end-to-end test.

## Device / remaining verification

ADB inventory showed no connected device. No device-side read/write, application launch, install, permission/configuration change, cleanup or runtime regression was performed. The installed APK is **not** updated by a host build. Updated Dev artifact is generated/ignored at `app/build/outputs/apk/debug/app-debug.apk`; owner/device acceptance remains pending.

The Test Tools feature uses its own desktop entry **TimeManager Test Tools**, separate from ordinary Dev app-details settings (which keep normal business minute values). Needed clarification: missing launcher vs screen cannot open vs edits/save do not work. Do not add guessed tool fixes or claim both symptoms resolved from the Home projection result. Phase4.2 device gates / Phase4.3 remain paused; no commit/push/merge/tag.
