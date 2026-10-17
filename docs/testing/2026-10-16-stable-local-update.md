# Local stable update — 2026-10-16

## Status: installed / owner will test

The owner explicitly authorized updating the installed daily TimeManager to current source. After reconnecting and restarting the host ADB server, the signed 1.0.1/code 2 candidate was installed with `install -r`; ADB returned **Success**. Android therefore accepted its update/signature compatibility. The owner confirmed the phone installation prompt, then explicitly requested installation only and no further tests. Post-install UI/data/service verification was not run; owner will test. No uninstall/clear-data, stable force-stop, permission/configuration change, commit, push or store publication was performed.

## Source and build

- Branch: `fix/accessibility-fallback-interventions`, starting commit `b905d33`; pre-existing dirty changes preserved.
- New source change in this deployment task: `app/build.gradle.kts` versionCode 1→2 and versionName 1.0→1.0.1; the Debug identity test now expects 1.0.1-dev.
- Uses current tested source including the configured Accessibility-component matcher. No new quota, timing, session, history, permission-request or overlay behavior was introduced by packaging.
- Both Debug/Release active configurations use normal time parameters. Debug's explicit unused regression fixture remains separate; Release has no fast regression fixture.
- Command: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain` — SUCCESS, 2m12s, 96 tasks (37 executed / 59 up-to-date).
- Fresh test execution: 29 suites / 147 tests, 0 failures/errors/skips. `git diff --check` PASS.

## Artifact

- Local APK: `app/build/outputs/local-stable-update/TimeManager-1.0.1-local.apk` (ignored).
- SDK metadata: package `com.example.timemanager`, label TimeManager, version 1.0.1 / code 2, no debuggable or testOnly flag.
- Built from release variant; signed locally with the existing Android debug identity to preserve compatibility with the owner's older locally deployed app. This is a local compatibility update, not a new store/release-key arrangement. No key created, copied into Git or permanent release signing configuration added.
- SDK apksigner verification passed v2/v3, one signer. Direct encoded-certificate comparison passed against the previously retained old TimeManager APK and existing keystore; no certificate fingerprints/file checksum reports were generated.
- Current installed APK's live read failed repeatedly, including after reconnect with transfer compression disabled. It was NOT used as successful signature proof. Do not keep repeating full-APK retrieval. The candidate matches the retained old APK/local signing identity; a future `install -r` must also pass Android's own installed-package signature validation. A mismatch must stop, never authorize uninstall/clear-data. No claim of an independent current-live-certificate comparison is made.

## Device preflight actually completed

- Verified development vivo X200s / Android 16, explicit serial on all commands (omitted here).
- Existing daily package: version 1.0 / code 1, debuggable; existing package installation metadata unchanged at the read. This confirms the old daily role was not an optimized signed Release distribution.
- Read-only `pm path`, package metadata, APK retrieval. Transfers ended before completion and ADB then listed no devices.
- On the next verified reconnect, read-only reads captured the daily app's existing managed/quota baseline and permission state privately. Actual user package choices, limits, data directory and original installation metadata are not included here. No app data was modified. Post-install state/history retention is still NOT YET VERIFIED.
- Installation ran: `adb -s <verified-serial> install -r <signed-release-apk>` (no `-t`, uninstall or clear-data fallback). ADB logged Incremental Install, then Streamed Install and Success. No further device command was run after success; the owner stopped the planned verification while the minimum pre-verification wait was in progress.

## Handoff

Installation complete. Per the owner's latest instruction, do not run post-install tests or change phone state. Data-preserving replacement was used, but data/UI/monitoring retention has not been independently verified after installation. Owner acceptance and any future Git/public release work remain separate.

Normal-parameter Dev from the previous task is unchanged on the phone. Its bounded test results and remaining vendor restrictions are in [Dev follow-up](2026-10-16-dev-timing-followup.md).
