# TimeManager

An Android digital wellbeing app built around intentional friction rather than hard blocking.

TimeManager introduces deliberate pauses when app usage becomes unconscious or prolonged. You choose the applications to manage, set personal limits, and decide what to do after a reminder. It is a personal project under active development, not a tamper-proof parental-control or device-management system.

中文：TimeManager 通过连续使用提醒、冷静页和专注模式，帮助用户减少无意识刷手机；配置和记录默认保存在设备上。

## Features

- Managed apps with editable daily usage limits and live usage tracking.
- Unlimited **+15 minute** extensions for the current usage day; the saved base limit stays unchanged.
- Per-app first session reminder, defaulting to **10 minutes** when unset, then **+8, +8, +5 minutes**, followed by further 5-minute intervals.
- Session Friction episodes and a one-shot re-entry gate per reminder stage; five minutes of genuine disengagement resets the episode.
- Focus Mode for selected apps and Deep Focus with necessary system exceptions and an explicit early-exit flow.
- Lightweight todos with Home/overlay summaries and staged reminders.
- Room-backed usage history, available-system-history backfill, daily/7-day/30-day statistics and per-app history.
- Permission health guidance and light/dark system-theme support.

A usage day runs from **04:00 to the following 04:00**, in the device time zone. A physical foreground session and a reminder episode are different concepts; see [current implementation](docs/PROJECT_STATE.md).

## UI

The app uses a restrained Material 3 dashboard, managed-app details and 7-/30-day statistics. A brand-neutral public illustration is not currently included: the previous private design reference contained third-party app logos and has been excluded from this repository's history. Raw device screenshots are also excluded. See [UI verification scope](docs/testing/UI_VERIFICATION.md) for what was actually checked.

## Architecture

UsageStats / realtime timer → repositories and evaluators → process-scoped InterventionCoordinator → overlay / Accessibility actions.

The UI observes ViewModel state. Preferences DataStore holds configuration and temporary daily state; Room holds usage snapshots, todos and reminder receipts. WorkManager schedules todo reminders, not realtime limit enforcement. The monitoring foreground service continues running when an intervention is displayed.

Built with Kotlin, Jetpack Compose Material 3, coroutines/StateFlow, Room, Preferences DataStore, WorkManager, UsageStatsManager, AccessibilityService, a foreground service and WindowManager overlays. [Architecture overview](docs/architecture/OVERVIEW.md).

## Permissions

| Permission / capability | Purpose |
| --- | --- |
| Usage Access | Read usage events and identify foreground app usage. |
| Display over other apps | Show deliberate-pause and focus overlays. |
| Notifications | Show ongoing monitoring, quota and todo reminders. Android may require runtime consent. |
| Accessibility | Perform HOME actions, observe app re-entry, and enforce Focus / Deep Focus. This is not required just to show an ordinary Session Reminder overlay. |
| Installed-app visibility | Resolve and select installed apps and their names/icons. |

Enable permissions yourself through the app's guidance. With Accessibility off, ordinary session reminders can fall back to overlay-only; HOME, re-entry gates and Focus/Deep Focus enforcement are incomplete. Permission settings and background behavior vary by Android vendor.

## Privacy

Current application source has no backend client, analytics SDK or usage-upload implementation, and the app manifest does not request Internet access. Configuration, usage history and todos are stored locally. Accessibility subscribes to window-state events and declares `canRetrieveWindowContent=false`; it does not retrieve screen content.

**Android backup is enabled in the current manifest.** Local-first does not mean excluded from OS-managed backup or device transfer. Debug logs may include package names and timing information: review/redact logs and screenshots before sharing. No claim of encryption or a security audit is made.

## Build

Requirements:

- Android Studio with support for the versions in the repository, or command-line Android SDK tools.
- Android SDK Platform **37** (minimum supported device API **26**).
- Network access for Gradle/dependency/toolchain downloads on a first build.
- A Java runtime to launch Gradle. The checked-in daemon criteria request **JDK 25**, provisioned through Foojay; install that JDK locally if automatic provisioning is unavailable. Java source compatibility is 11, which is not the Gradle daemon version.

Clone the repository, open it in Android Studio, let Gradle sync, and select your SDK installation. For command-line builds, provide `ANDROID_HOME` or a local, untracked `local.properties`. No private signing key or developer-specific absolute path is needed.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

On macOS/Linux use `sh ./gradlew` (the wrapper currently has no executable bit). Debug builds are development-signed; the current Release build produces an **unsigned** APK and is not a ready-to-publish store artifact. No APK is committed. The current `versionName=1.0` is legacy build metadata, not a published release or a stability guarantee.

Host unit tests do not install anything on a phone. Read [DEVICE_SAFETY.md](DEVICE_SAFETY.md) before device testing. In particular, do not run the default `connectedDebugAndroidTest` workflow on the user's vivo X200s: its cleanup is unsafe for persistent user data.

## Status and contributing

Primarily tested on vivo X200s / Android 16. Cross-vendor behavior, full permission-revocation coverage, large-font/landscape validation and real incoming-call behavior still need broader validation. See [open issues](docs/OPEN_ISSUES.md), [sanitized test reports](docs/testing/README.md), [changelog](CHANGELOG.md) and [contribution guide](CONTRIBUTING.md).

This is the independent open-source candidate repository, currently private pending owner review; migration does not publish an APK, create a GitHub Release or change visibility. Dependency and visual-asset attribution notes are in [third-party notices](docs/THIRD_PARTY_NOTICES.md).

## License

Copyright 2026 FPhoenix28. Project-authored material is licensed under the [Apache License 2.0](LICENSE). Third-party dependencies, trademarks and assets retain their own terms.
