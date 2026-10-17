# Debug / Release Split Implementation Plan

**Goal:** Independently install TimeManager Dev and TimeManager without sharing app data or permissions.

**Architecture:** Keep the namespace and Kotlin packages unchanged. Debug adds an application ID suffix and resource overrides; runtime identity continues to come from the application context. Keep existing debug/release timing source sets and explicit test opt-in.

**Tech Stack:** Android Gradle build types, Android resources, Kotlin, JVM tests, SDK APK inspection.

**Spec:** User-provided Debug / Release split request, 2026-10-15.

## Constraints

- Release applicationId and namespace remain `com.example.timemanager`.
- Debug applicationId is `com.example.timemanager.debug`, version suffix `-dev`.
- No commit/push, signing key generation, uninstall, data clear or production-app writes.
- Current branch and master share the same committed base; preserve existing uncommitted fallback work.
- Execute in this session; no delegated agents or extra worktree required.

## Tasks

- [x] **Build identity and labels.** Modify `app/build.gradle.kts`; add `app/src/debug/res/values/strings.xml`. Keep manifest resource labels. Verify built APK package, version, labels and flags with aapt2.
- [x] **Runtime identity and naming.** Inspect services, policies, permissions, PendingIntents, providers and storage. Modify notification helpers to resolve app-name resources and variant-specific explicit-intent actions. Correct `ExampleInstrumentedTest`; add JVM Focus tests for both own-package identities and Debug identity assertions. Inspect Release source-set parameters and APK. Run host unit tests; never run connected instrumentation.
- [x] **Document development boundaries.** Update `AGENTS.md`, `DEVICE_SAFETY.md`, `CONTRIBUTING.md` and `docs/PROJECT_STATE.md`. Default device writes target Dev; production writes need explicit intent. Debug installation uses `install -r -t`; failed installation stops without destructive fallback.
- [x] **Host validation and report.** Run Debug unit tests and Debug/Release builds, APK metadata/merged-provider inspection and `git diff --check`. Write `docs/testing/DEBUG_RELEASE_SPLIT_REPORT.md` distinguishing host and device evidence, existing changes and untouched production app. This project does not expose a `testReleaseUnitTest` task; do not add test infrastructure just for this change.
- [x] **Device installation and isolation.** On the connected vivo X200s, installed Dev only with `-r -t`, confirmed Success and waited 71 seconds before phone interaction. Verified both packages, independent UIDs/directories, fresh Dev onboarding, app-info name/version and no inherited Usage/Overlay/Notification grants. Production installation metadata unchanged; no production writes.
- [x] **System UI names.** After reconnection, verified both Accessibility rows, Dev's service description, both launcher labels and launching Dev from its identified icon. Current vivo grid visually truncates long launcher names. No reinstall or permission changes; production installation metadata still unchanged.
- [x] **Authorization, notification and update persistence checks.** On 2026-10-16 enabled Dev's own Usage/Overlay/Notification and vendor installed-app-list access; checked branding, simultaneous ongoing notifications and managed selection/quota persistence across update. Normal Dev APK restored, test item removed, temporary battery policy restored and owner-authorized production monitoring restarted.
- [x] **Bounded independent timing/intervention regression.** The earlier runtime run failed (retained in `docs/testing/DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md`). The later `docs/testing/2026-10-16-dev-timing-followup.md` passed bounded timing, fallback Session/Daily/+15 and screenshot-only connected HOME/Gate/Continue/no-repeat checks, followed by normal Dev/settings restoration. This does not establish full vendor coverage or owner acceptance.
