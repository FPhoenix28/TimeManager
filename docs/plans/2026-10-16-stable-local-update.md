# Stable local update Implementation Plan

**Goal:** Update the owner's installed `com.example.timemanager` to current source, retaining its own data; do not promote Git or publish a binary.

**Architecture:** Build the existing release variant with production time parameters. Verify its application ID/label and sign locally with the existing compatible identity only if the installed certificate matches. Dev remains a separate package.

**Tech Stack:** Gradle, Android SDK aapt2/apksigner, ADB, current JDK.

**Spec:** Owner request in this chat on 2026-10-16: iterate the installed stable app to latest.

## Constraints

- Preserve the dirty worktree, user app data and other applications. No uninstall, clear-data, instrumentation cleanup, signing-key replacement or fallback.
- No certificate fingerprints, file digests or checksum reports. Compare encoded signer certificates directly; verify the APK signature with SDK tooling.
- No commit, push, merge or public release. Existing background/vendor limitations remain disclosed.
- Remind the owner about USB installation approval; require Success and at least 60 seconds before further device actions.

## Steps

- [x] Inspect installed package and existing quota/managed-state baseline read-only. Compare retained old APK/keystore/candidate signer identity; live APK export repeatedly disconnects and is not successful signature proof. Final installed-identity compatibility must be enforced by Android's non-destructive `install -r` check.
- [x] Bump `app/build.gradle.kts` to `versionCode=2`, `versionName="1.0.1"`; update the matching Debug identity assertion to `1.0.1-dev`. No business-rule changes.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`, whitespace check. Require all tests/builds pass. 147 tests passed.
- [x] Sign release output locally using the existing debug keystore that matches the retained old daily APK. This is not a new store signing arrangement. No keys committed or permanent release signing configuration introduced; Android must still accept its signature during `install -r`.
- [x] SDK checks: package `com.example.timemanager`, name TimeManager, version 1.0.1/code 2; not debuggable, not testOnly; signature verified and matching retained old APK. Live installed identity comparison not completed independently.
- [x] Install exactly `adb -s <verified-serial> install -r <signed-release-apk>` after the installation reminder. Success; phone confirmation received; no destructive fallback.
- [ ] Post-install verification CANCELLED by owner: installation only, owner will test. No device command after Success. Do not resume this step automatically.
- [x] Record actual installation and verification boundary in `docs/testing/`; update state pointer. Owner acceptance and any Git action remain distinct.

## Progress

Build, local signing and data-preserving installation complete; see [deployment report](../testing/2026-10-16-stable-local-update.md). Android accepted the update with Success. No additional phone action or tests after Success: owner requested self-testing. No Git/publication operation performed.
