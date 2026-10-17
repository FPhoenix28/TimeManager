# Contributing

TimeManager is in continuous development. This is the everyday development workflow, not a release process. Read [PROJECT_STATE](docs/PROJECT_STATE.md), [open work](docs/OPEN_ISSUES.md) and the [full workflow](docs/DEVELOPMENT_WORKFLOW.md) before changing code.

## Build roles

| Role | Build | Package | Display name |
| --- | --- | --- | --- |
| Development/testing | Debug | `com.example.timemanager.debug` | TimeManager Dev |
| User's daily stable app | Release identity | `com.example.timemanager` | TimeManager |

Both apps have independent data and permissions. Keep the namespace/Kotlin packages unchanged. Debug has a `-dev` version suffix and debug signing; Release signing is not configured yet. The stable app on the phone is the user's chosen installed version, not something automatically replaced after each build. Both variants default to product timing; fast regression fixtures are Debug-only and require explicit opt-in.

## Development lifecycle

Code → `testDebugUnitTest` → `assembleDebug` → verify APK identity → install Dev → device validation → **user acceptance** → explicitly requested commit → optional, explicitly requested push / master integration.

1. Check `git status`, current branch and latest commit; preserve existing edits. Prefer `feat/*` or `fix/*` for feature/bug work. Make focused changes in the existing architecture.
2. Run `gradlew.bat :app:testDebugUnitTest` and `git diff --check` for code changes; build Debug for deployment. Use `:app:assembleRelease` when useful as a build check, not a publication action. For docs-only tasks, validate the docs/links/diff and report code/device tests as not run.
3. Before installation, verify APK package `com.example.timemanager.debug` and label TimeManager Dev with an SDK tool. Stop if identity cannot be verified. Use `adb -s <verified-serial> install -r -t <debug-apk>`; failed installation stops without uninstall/clear-data fallback.
4. Read [DEVICE_SAFETY](DEVICE_SAFETY.md) for permission setup, installation confirmation/wait, dual-instance isolation and cleanup. Test Dev only; a conflicting stable monitor must be paused by the user or under current-task explicit authorization, not automatically force-stopped.
5. Rebuild/reinstall/retest Dev as needed without committing each iteration. Keep time shortcuts Debug-only and explicitly enabled; restore temporary defaults/logging before handoff. `BuildConfig.DEBUG` alone is not an instruction to overwrite user limits.
6. Provide a [DEVELOPMENT_TEST_REPORT](docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md) with passed, failed and unverified cases, then await actual user experience/acceptance. Automated success is not user acceptance.
7. After acceptance, suggest a focused commit if appropriate. Codex stages/commits only when explicitly asked; pushing requires its own explicit request. Master integration also follows the user's decision. Small, low-risk verified changes need not use a PR; larger features should use branch → test → acceptance → integration.

`master` is the intended accepted source baseline. It may be newer than the user's installed stable app. A separate request such as “update my stable app” is required to update `com.example.timemanager`; a commit, push or successful test does not authorize it. No GitHub Release workflow is required for an ordinary development iteration.

## Hygiene

Prefer `feat:`, `fix:`, `refactor:`, `perf:`, `test:`, `docs:` or `chore:` commit prefixes. Add relevant regressions for behavior changes and use fake data in tests. Keep APK/AABs, signing keys, credentials, temporary test overrides, raw device screenshots, private notifications and local diagnostics out of source history. No automatic uninstall, clear-data or destructive cleanup; default connected instrumentation is prohibited on the user's vivo X200s.

Current implementation belongs in `docs/PROJECT_STATE.md`, unresolved work in `docs/OPEN_ISSUES.md`, plans in `docs/plans/`, and sanitized validation in `docs/testing/`. Historical records are evidence for their date, not current operating instructions; do not resume retired `tasks/` journals.
