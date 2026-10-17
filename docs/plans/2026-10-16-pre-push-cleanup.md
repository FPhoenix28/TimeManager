# Pre-push cleanup Implementation Plan

**Goal:** Prepare a clear, validated source change set; stop before push, with no staging/commit/merge unless separately authorized.

**Architecture:** Preserve the current branch and dirty changes. Review changed source and new files, reconcile current-state documentation, exclude private/generated artifacts, and validate on host only. No business-rule changes or phone operations.

**Tech Stack:** Git, PowerShell, Gradle, existing Kotlin/JVM tests.

**Spec:** Owner request: prepare for GitHub, organize the repository, stop before push.

## Tasks

- [x] Review `git diff`, untracked source/docs, current branch and configured origin. Separate fallback implementation, build isolation/versioning, and workflow/docs into proposed commit groups.
- [x] Inspect changed/new candidates for local paths, device identifiers, credentials, APKs, raw logs/screenshots and active temporary timing/debug diagnostics. Keep intentional Debug-only fixtures distinct from active configuration.
- [x] Reconcile `docs/PROJECT_STATE.md`, `docs/OPEN_ISSUES.md`, testing index and current plans/reports with final Dev regression and daily APK installation. Historical reports remain historical; do not inflate user acceptance or full device coverage.
- [x] Run host unit tests and Debug/Release build checks as appropriate; inspect results, document links and whitespace. Gradle reused unit/build results; existing XML has 147 passing tests. No ADB or instrumentation.
- [x] Write [preparation report](../testing/2026-10-16-pre-push-preparation.md) with actual checks, excluded artifacts, remaining issues and proposed commits. No `git add`, commit, checkout/merge, fetch/push or GitHub mutation.
