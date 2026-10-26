# Stable local update — 2026-10-25

## Authorization and source

- Owner explicitly requested updating the phone's stable TimeManager to the accepted current development code.
- Branch: `feat/rules-engine`; source commit: `8a15a2f` (`refactor: unify intervention rule decisions`). Starting worktree was clean.
- No source/version change. This is a local stable-app update, not publication of v0.10.0. Display version remains **0.9.0 / code 3**.

## Build and APK verification

- `gradlew.bat :app:assembleRelease --console=plain`: **PASS** (2m 9s).
- SDK metadata: `com.fphoenix28.timemanager`, label **TimeManager**, version **0.9.0**, code **3**, non-debuggable, non-testOnly.
- `apksigner verify --verbose`: **PASS**, v2 signature valid, one signer. Existing local Release signing configuration used; no signing secrets were read or printed by the agent.
- Previous accepted host baseline: Debug 574 tests / 62 suites, Release 548 / 59; failures/errors/skips 0. Not rerun for this unchanged-source installation task.

## Device installation and bounded checks

- Verified owner vivo X200s / Android 16 / API 36; ADB explicitly targeted the verified device. Stable package already installed. Dev monitoring was not running.
- Executed `adb -s <verified-device> install -r <release-apk>`: **Success**. No `-t`, uninstall, clear-data or destructive fallback.
- Owner confirmed the USB installation prompt. Installation status was checked after at least one minute (74 seconds recorded).
- Stable package's update time changed to this task's installation; original first-install time remained unchanged. Existing installed package identity was retained.
- Dev package's update time remained unchanged. No Dev installation or configuration change.
- No application launch, functional smoke test, permission change, user-data inspection or other-app operation. Configuration/history preservation was not individually revalidated; the update used Android's data-preserving replacement installation, not a backup/restore or data clear.

## Handoff / Git

- Installation: **PASS**. Functional owner validation: not performed by Codex this task.
- APK remains ignored at `app/build/outputs/apk/release/app-release.apk`; signing material remains outside Git.
- This report is the only new repository file. No source edit, commit, push, merge, tag or Release publication.
