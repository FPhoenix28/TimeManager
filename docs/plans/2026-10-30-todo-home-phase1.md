# Todo Home Phase 1 Implementation Plan

**Goal:** Keep today's usage prominent and place a compact, actionable three-todo preview directly beneath it.

**Architecture:** Reuse TodoViewModel's existing dayState, TodoRow and TodoInput. Only the todo section subscribes to todo state. Keep all persistence, ordering, carry-over and reminder rules unchanged.

**Tech Stack:** Kotlin, Compose Material 3, existing Room/StateFlow data chain; host-only Compose tests with Robolectric.

**Spec:** Owner's “TimeManager — Phase 1：首页今日待办体验升级” request in this chat.

## Constraints

- Work on feat/todo-experience from the verified latest master; no commit/push/merge.
- No device operations, Release APK build, version changes or Phase 2 work.
- No changes to repositories, database, 04:00 usage day, Rules Engine, monitoring or Focus.
- Host UI assertions are not owner visual/device acceptance.

## Implementation

- [x] Update `ui/todo/TodoComponents.kt`: Material 3 card; title, unfinished count and view-all; remember the first three unfinished records without sorting; retain keyed Checkbox rows, compact empty state, overflow count, TodoInput and error message.
- [x] Update `ui/home/HomeScreen.kt`: move todos immediately after todaySummary; retain all modes and app entries. Make the existing Loading/Error fallback scrollable with usage-unavailable summary, todos, modes and status message. Add IME padding to keep inline input reachable.
- [x] Add Debug host-only UI test configuration in `app/build.gradle.kts` / `gradle/libs.versions.toml` and tests under `app/src/testDebug/.../ui/todo/`. Use a fake TodoDao with the real Repository/ViewModel; do not open user data or start the production Application.
- [x] Exercise 0/1/3/5 tasks, row completion and promotion, adding, view-all/full-page actions, success/fallback order and scrollability, light/dark themes, long titles, narrow width and large font. Inspect constraints/semantics locally; record device/visual coverage separately.
- [x] Run `:app:testDebugUnitTest`, `:app:assembleDebug` and `git diff --check`; review the final diff and report results without staging or committing.

## Local validation — 2026-10-30

- Full Debug suite: **587 tests / 63 suites; zero failures, errors or skips**. Debug assembly and whitespace check passed.
- The 13 new Compose tests passed using the real TodoRepository/ViewModel with an in-memory fake DAO. After tightening the narrow/large-font viewport to 280 × 480dp at fontScale 2, all 13 were run again and passed. No production change followed the full-suite run.
- Reviewed generated native-rendered light/dark Home/card images under ignored `app/build/reports/todo-home-phase1/`. Header adapts to large fonts; long task titles retain two-line ellipsis and scroll-accessible controls.
- These are host UI interaction/layout checks, not owner visual or device acceptance. Real keyboard/insets, vendor rendering, rotation and physical scrolling performance remain for owner review.
- Device operations: none. Release build and version changes: none. Owner accepted Phase 1 and authorized its standalone checkpoint commit when starting Phase 2; no push/merge/tag authorized.
