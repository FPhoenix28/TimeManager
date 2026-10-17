# DEBUG_RELEASE_SPLIT_REPORT

Date: 2026-10-15. Scope: build-variant identity, naming, isolation and development rules. No changes to quota, session, UsageStats, history or overlay business rules in this task.

## Result

| Property | Debug | Release |
| --- | --- | --- |
| applicationId | `com.example.timemanager.debug` | `com.example.timemanager` |
| Display / launcher name | TimeManager Dev | TimeManager |
| Version name | `1.0-dev` | `1.0` |
| Namespace / Kotlin packages | `com.example.timemanager` | unchanged |
| Accessibility label | TimeManager Dev 辅助服务 | TimeManager 专注与静心模式 |
| Signing | Default Android debug signing | **release APK currently unsigned** |
| APK output | `app/build/outputs/apk/debug/app-debug.apk` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| debuggable flag | true | absent / false by default |
| testOnly flag in these CLI-built APKs | absent | absent |
| Default time overrides | disabled | disabled |

Debug may still be marked testOnly by Android Studio Run. Nothing disables or overrides that normal behavior. Debug installs use `install -r -t`. Release must never require `-t`; no signing key or password was created. No misleading rename of the unsigned Release output was performed.

## Changed files — this task only

- `app/build.gradle.kts`: Debug `.debug` application ID suffix and `-dev` version suffix; existing Release configuration retained.
- `app/src/debug/res/values/strings.xml` (new): overrides only app name, Accessibility label and description. Main strings remain unchanged.
- `app/src/main/java/com/example/timemanager/notifications/MonitorNotificationHelper.kt`: app-name resource in monitoring title, initial message and channel description.
- `app/src/main/java/com/example/timemanager/notifications/NotificationHelper.kt`: resource-based alert title.
- `app/src/main/java/com/example/timemanager/notifications/DeepFocusNotificationHelper.kt`: resource-based title and variant-specific explicit notification action.
- `app/src/main/java/com/example/timemanager/notifications/TodoNotificationHelper.kt`: resource-based title and variant-specific explicit notification action.
- `app/src/androidTest/java/com/example/timemanager/ExampleInstrumentedTest.kt`: context assertion uses `BuildConfig.APPLICATION_ID`. This instrumentation test was not run.
- `app/src/test/java/com/example/timemanager/domain/focus/FocusEnforcementPolicyTest.kt`: two runtime own-package regressions for Debug and Release identities.
- `app/src/testDebug/java/com/example/timemanager/config/DebugTimeConfigTest.kt`: Dev package and version assertion.
- `AGENTS.md`, `DEVICE_SAFETY.md`: Dev-only default testing; production writes need explicit intent; standard Debug `-r -t` installation, manual confirmation wait and no destructive fallback.
- `CONTRIBUTING.md`: short Build variants section; user README unchanged.
- `docs/PROJECT_STATE.md`: coexistence, timing separation and current validation scope.
- `docs/plans/2026-10-15-debug-release-split.md` (new): scoped execution checklist.
- This report (new).

## Accessibility / notification naming behavior

Application and Activity labels already referenced `@string/app_name`; service label already referenced `@string/focus_accessibility_label`; service XML already referenced its description resource. No service class was renamed. SDK resource dumps confirm all Debug overrides and unchanged Release labels.

All four notification helpers resolve branding from current app resources. Monitoring notifications while a target app is active retain that target's app name as their title, as before. Channels and notification IDs stay unchanged: Android isolates them by applicationId. PendingIntents remain explicit `Intent(context, MainActivity::class.java)` and are created by the current app; Todo/Deep Focus actions now use `BuildConfig.APPLICATION_ID`. Existing MainActivity action consumers use those same constants, preserving Release action values.

## Hard-coded package references found / fixed

The repository source search found these relevant quoted literals; package/import declarations were also inspected but intentionally not rewritten:

| Reference | Classification / result |
| --- | --- |
| `app/build.gradle.kts` namespace and default applicationId | Intentional production values, unchanged |
| Todo and Deep Focus action strings | Previously fixed namespace strings; now variant-derived, even though explicit PendingIntents already scoped the target |
| `ExampleInstrumentedTest` target context | Fixed to use final `BuildConfig.APPLICATION_ID` |
| Focus policy test fixtures | Intentional test inputs; expanded to exercise both variants |
| New Debug build identity assertion | Intentional expected Dev ID |

No production own-package comparison still uses a hard-coded Release ID. Verified paths include:

- `InterventionRuntime`: `BuildConfig.APPLICATION_ID` supplies coordinator identity.
- Accessibility Focus enforcement: current Service `packageName`.
- `DeepFocusEssentialAppResolver`: `context.packageName`.
- `FocusModeRepository`: caller supplies current `packageName`, and filtering uses that value.
- `UsageStatsReader`: current `context.packageName` for AppOps and own-package exclusion.
- Usage/Overlay permission helpers: current context package.
- `AccessibilityPermissionManager`: `ComponentName(context, TimeManagerAccessibilityService::class.java)`. Debug's owning package differs while its service class name correctly retains the original Kotlin namespace.

## Provider / authority check

No app-defined FileProvider, custom ContentProvider, sharedUserId or fixed custom authority exists in source manifests. The dependency-merged manifest includes AndroidX Startup (including WorkManager initialization):

| Item | Debug | Release |
| --- | --- | --- |
| Startup authority | `com.example.timemanager.debug.androidx-startup` | `com.example.timemanager.androidx-startup` |
| Generated receiver permission | `com.example.timemanager.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `com.example.timemanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

SDK APK manifest dumps confirmed both distinctions and unchanged fully qualified Activity/Service class names. No authority collision was found. No provider rewrite was necessary.

## Storage and permissions

Room uses `Room.databaseBuilder(context.applicationContext, ..., "timemanager.db")`. All existing DataStore delegates use the current application context. There is no SharedPreferences cross-package store, createPackageContext access, shared UID or shared external database introduced by this work.

Separate application IDs therefore give separate internal data sandboxes, Room databases, DataStore files, application UIDs, permissions, Accessibility service components and notification namespaces. No migration/copy of production configuration into Dev is performed. Dev starts with its own onboarding and must receive its own authorizations. Runtime authorization/coexistence on the phone remains unverified, not presumed tested from a build.

## Debug-only configuration isolation

Existing `src/debug/.../BuildTimeConfig.kt` and `src/release/.../BuildTimeConfig.kt` were retained unchanged. Both default to normal product parameters, and user base quotas still come from their existing repositories. `fastRegression` is an explicit opt-in fixture compiled only into Debug; it does not persist test values into production keys.

Verified with generated BuildConfig, compiled classes and APK DEX symbol inspection:

- Debug: DEBUG=true, final ID `.debug`, `fastRegression` present, default overrides disabled (unit test).
- Release: DEBUG=false, original ID, no `fastRegression` field/getter or APK symbol. Compiled initialization uses overrides=false, 900s / 1800s notification thresholds and 900s extension; Session and quota settings retain existing product behavior.
- No `android.injected.testOnly=false` was added; no shared timing algorithm was duplicated.

## Unit tests / builds

Successful command:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
git diff --check
```

**BUILD SUCCESSFUL in 1m 21s**, 96 actionable tasks (35 executed / 61 up-to-date).

XML result count: **28 suites / 144 tests / 0 failures / 0 errors / 0 skipped**. Three new tests over the current 141-test fallback worktree baseline:

1. Each variant excludes its own runtime identity from Focus blocking.
2. Dev does not mistake Release for itself, and vice versa, when both are explicitly selected in a pure policy test.
3. Debug BuildConfig has the independent package and dev version.

Debug build: PASS. Release build: PASS, unsigned. Whitespace check: PASS. Existing fallback tests also ran, but passing JVM tests does not establish full fallback device acceptance.

An initial expanded command included the optional `:app:testReleaseUnitTest`; this project exposes no such task, so Gradle stopped at task selection. The unused optional test source was removed, no test infrastructure was added, and the required command above then succeeded. Release timing is checked from its actual compiled class/APK, not an unexecuted test.

## APK metadata verification

Used installed SDK Build Tools 36.0.0 `aapt2`: `dump badging`, `dump resources`, `dump xmltree ... --file AndroidManifest.xml`. Verified final IDs, versions, launcher names, Accessibility resources, debug/testOnly flags and provider/permission names from both built APKs. Release is neither debuggable nor testOnly. Debug's plain CLI build is debuggable and not testOnly; Studio may legitimately inject testOnly later.

Generated BuildConfig and `javap` output confirmed unchanged namespace, variant-specific IDs and Release parameter initialization. APK DEX inspection found `fastRegression` only in Debug. No digest/checksum generation was used.

## Device install test actually run

The following covers initial installation/name checks on 2026-10-15. The subsequent owner-authorized runtime run on 2026-10-16 is recorded in [Dev runtime acceptance](DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md): authorization/notification/persistence checks passed, but independent timing/intervention acceptance did **not** pass. Production was temporarily stopped only after explicit authorization and reopened at completion; no production APK was installed over.

The initial host-only phase had no connected device. After the owner connected the known vivo X200s / Android 16, a follow-up device check ran:

1. Verified serial/model and final APK ID internally; Dev was not previously installed. Production was present.
2. Installed only Dev with `adb -s <verified-serial> install -r -t <app-debug.apk>`. The owner confirmed the USB prompt; installation returned **Success**. The first subsequent phone operation was 71 seconds after installation began.
3. Explicit user-0 package inventory showed both `com.example.timemanager` and `com.example.timemanager.debug`. The initial all-profile query also reported a vendor profile access restriction; the user-0 query completed normally.
4. Launched `com.example.timemanager.debug/com.example.timemanager.MainActivity`. Activity/process inspection and screenshot confirmed a successful independent first-launch onboarding screen.
5. Read-only package metadata confirmed different app IDs / UIDs and `/data/user/0/<applicationId>` directories. Dev initialized its own app-specific preferences; no production preferences were copied or modified.
6. Read-only AppOps/package inspection showed production Usage/Overlay allowed and notifications granted, while Dev Usage/Overlay remained default and notifications were not granted. These existing grants were not inherited. No permissions were toggled.
7. Android's app-info page visibly showed **TimeManager Dev**, version **1.0-dev**.
8. Production code path, version, first-install time, last-update time and data-directory metadata matched the before-install snapshot exactly. Production was not installed over, launched, force-stopped or reconfigured by this test.
9. Opened Accessibility Settings to inspect the downloaded-service labels without toggling services. During initial list navigation, ADB disappeared; subsequent inventories were empty and device writes stopped. That interrupted screenshot was not accepted as evidence.
10. After the owner reconnected the same device, completed the system UI check without reinstalling: the downloaded Accessibility list showed **TimeManager Dev 辅助服务** and **TimeManager 专注与静心模式** as separate entries. Dev's service-detail page also showed its correct test-service description. Both service switches remained off; no permission was changed.
11. The launcher showed two separate icons. Its UI hierarchy exposed the complete labels **TimeManager** and **TimeManager Dev**; the current vivo grid visually ellipsizes both long labels, so desktop-only visual distinction is limited despite correct names. Tapping the specifically identified Dev icon resumed the Dev Activity. The final screen is Dev, not production. Production installation metadata still matched the original snapshot.

- Existing release app touched? **NO writes**; read-only package/AppOps inspection only.
- Existing user data touched? **NO direct modifications or clearing**. Only the new Dev app created its own normal startup files. The existing app's normal background activity was not frozen or interfered with.
- Previous test APK restored by this task? **NO**. This split test does not restore the earlier single-package deployment.
- No connected instrumentation, uninstall, clear-data, recovery installation, permission change or user-file deletion occurred. Raw screenshots remain ignored local material and are not published here.

## Still unverified

- Full independent foreground timing/intervention behavior on Dev remains blocked by the failed runtime run. Both ongoing notifications and Dev's own grants/configuration persistence across updates have now been verified; see the follow-up report. Android Studio Run testOnly installation itself was not separately exercised.
- Production signing/distribution remains separate work; no key was generated.
- Earlier fallback/history reconciliation issues are outside this build-only task and are not resolved by splitting package IDs.

## Git diff / status

Branch: `fix/accessibility-fallback-interventions`. Local master and current committed base were both `b905d33` when inspected. Existing fallback implementation and the minimum-release roadmap were already dirty/untracked at entry and were preserved, not reset, reimplemented or silently attributed to this task.

`git diff --stat` snapshot (tracked worktree changes, **includes pre-existing fallback edits**): **28 files changed, 395 insertions(+), 84 deletions(-)**. Newly added files are not counted by that command until staged; nothing was staged.

`git status --short` scope:

- This task: modified AGENTS, CONTRIBUTING, DEVICE_SAFETY, app Gradle, four notification helpers, instrumented context assertion, Focus policy tests, Debug identity test and PROJECT_STATE; new Debug resource override, split plan and this report.
- Preserved existing edits: manifest stopWithTask, MainActivity fallback availability, intervention request, UsageTimeRepository, coordinator/friction/permission/runtime/service/overlay fallback implementation and their existing tests, OPEN_ISSUES; existing untracked fallback helpers/tests/plan/report and minimum-release roadmap.
- No changes to debug/release BuildTimeConfig source, production ID, namespace, Kotlin package declarations, main labels or signing configuration.

**No commit, push, branch reset, production-app write or user-data cleanup.** Await user acceptance before any submission decision.

### Command snapshots

`git diff --stat` (tracked files only; pre-existing edits included):

```text
 AGENTS.md                                          |   5 +-
 CONTRIBUTING.md                                    |   9 ++
 DEVICE_SAFETY.md                                   |  18 ++--
 app/build.gradle.kts                               |   4 +
 .../example/timemanager/ExampleInstrumentedTest.kt |   4 +-
 app/src/main/AndroidManifest.xml                   |   1 +
 .../java/com/example/timemanager/MainActivity.kt   |  10 +-
 .../data/model/LimitInterventionRequest.kt         |   6 ++
 .../data/repository/UsageTimeRepository.kt         |  26 ++++-
 .../domain/intervention/InterventionCoordinator.kt |  29 +++--
 .../timemanager/monitor/SessionFrictionManager.kt  |  18 +++-
 .../notifications/DeepFocusNotificationHelper.kt   |   5 +-
 .../notifications/MonitorNotificationHelper.kt     |   7 +-
 .../notifications/NotificationHelper.kt            |   2 +-
 .../notifications/TodoNotificationHelper.kt        |   5 +-
 .../permissions/PermissionHealthManager.kt         |  13 +++
 .../permissions/PermissionHealthState.kt           |   6 +-
 .../service/TimeManagerAccessibilityService.kt     |   2 +-
 .../timemanager/service/UsageMonitorService.kt     | 118 ++++++++++++++++-----
 .../ui/intervention/SessionFrictionContent.kt      |  21 ++--
 .../ui/permissions/PermissionSettingsScreen.kt     |  33 +++++-
 .../domain/focus/FocusEnforcementPolicyTest.kt     |  16 +++
 .../intervention/InterventionCoordinatorTest.kt    |  19 +++-
 .../monitor/SessionFrictionManagerTest.kt          |  68 ++++++++++++
 .../permissions/PermissionHealthStateTest.kt       |   9 +-
 .../timemanager/config/DebugTimeConfigTest.kt      |   6 ++
 docs/OPEN_ISSUES.md                                |   1 +
 docs/PROJECT_STATE.md                              |  18 +++-
 28 files changed, 395 insertions(+), 84 deletions(-)
```

`git status --short`:

```text
 M AGENTS.md
 M CONTRIBUTING.md
 M DEVICE_SAFETY.md
 M app/build.gradle.kts
 M app/src/androidTest/java/com/example/timemanager/ExampleInstrumentedTest.kt
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/example/timemanager/MainActivity.kt
 M app/src/main/java/com/example/timemanager/data/model/LimitInterventionRequest.kt
 M app/src/main/java/com/example/timemanager/data/repository/UsageTimeRepository.kt
 M app/src/main/java/com/example/timemanager/domain/intervention/InterventionCoordinator.kt
 M app/src/main/java/com/example/timemanager/monitor/SessionFrictionManager.kt
 M app/src/main/java/com/example/timemanager/notifications/DeepFocusNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/MonitorNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/NotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/TodoNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/permissions/PermissionHealthManager.kt
 M app/src/main/java/com/example/timemanager/permissions/PermissionHealthState.kt
 M app/src/main/java/com/example/timemanager/service/TimeManagerAccessibilityService.kt
 M app/src/main/java/com/example/timemanager/service/UsageMonitorService.kt
 M app/src/main/java/com/example/timemanager/ui/intervention/SessionFrictionContent.kt
 M app/src/main/java/com/example/timemanager/ui/permissions/PermissionSettingsScreen.kt
 M app/src/test/java/com/example/timemanager/domain/focus/FocusEnforcementPolicyTest.kt
 M app/src/test/java/com/example/timemanager/domain/intervention/InterventionCoordinatorTest.kt
 M app/src/test/java/com/example/timemanager/monitor/SessionFrictionManagerTest.kt
 M app/src/test/java/com/example/timemanager/permissions/PermissionHealthStateTest.kt
 M app/src/testDebug/java/com/example/timemanager/config/DebugTimeConfigTest.kt
 M docs/OPEN_ISSUES.md
 M docs/PROJECT_STATE.md
?? app/src/debug/res/
?? app/src/main/java/com/example/timemanager/domain/intervention/InterventionPresentation.kt
?? app/src/main/java/com/example/timemanager/permissions/AccessibilityRuntimeState.kt
?? app/src/test/java/com/example/timemanager/domain/intervention/InterventionPresentationPolicyTest.kt
?? docs/plans/2026-10-14-accessibility-fallback.md
?? docs/plans/2026-10-15-debug-release-split.md
?? docs/plans/2026-10-15-minimum-release-roadmap.md
?? docs/testing/ACCESSIBILITY_FALLBACK_REPORT.md
?? docs/testing/DEBUG_RELEASE_SPLIT_REPORT.md
```
