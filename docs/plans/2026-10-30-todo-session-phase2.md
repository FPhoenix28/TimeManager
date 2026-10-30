# Todo Session Phase 2 Implementation Plan

**Goal:** Make unfinished todos visible in Session cooling pages without changing intervention behavior.

**Architecture:** Add a Session-only read-only card consuming the existing in-memory TodoSummary. Keep Daily's TodoOverlaySummary unchanged. Opt Session into separately scrollable information and a reserved portrait action area in the shared responsive scaffold; all other callers retain their existing layout.

**Tech Stack:** Kotlin, Compose Material 3, existing TodoSummaryProvider and Debug host-only Robolectric tests.

**Spec:** Owner's “TimeManager — Phase 2：连续使用冷静页 UI 升级” request in this chat.

## Boundaries

- Phase 1 accepted and checkpoint committed as `08e54d0`; continue on feat/todo-experience.
- No Phase 2 commit, push, master operation, version change, Release build or phone operation.
- No timer/deadline, Gate phase, callback, Episode, Rules Engine, Daily, Focus, persistence or notification changes.
- No third action or delayed Deep Focus implementation.

## Steps

- [x] Add `ui/intervention/SessionTodoCard.kt`: hide when empty, title/count and invitation, first three tasks in supplied order, mild first-item emphasis, overflow count, theme colors, two-line/compact ellipsis.
- [x] Update `SessionFrictionContent.kt`: use the Session-only card; make existing HOME action primary and Continue secondary while preserving REENTRY wording, callbacks and the shared actionsEnabled predicate.
- [x] Update `ResponsiveInterventionScaffold.kt`: add a default-off portrait action reservation option and enable it only for Session; keep existing landscape layout and other callers unchanged.
- [x] Add `app/src/testDebug/.../ui/intervention/SessionTodoUiTest.kt`: 0/1/3/5 summaries, titles/order/overflow, live summary updates, countdown/disabled actions/deadline, REMINDER/REENTRY/fallback behavior, narrow/large-font and landscape layouts, callbacks, Daily regression and light/dark native renders.
- [x] Run related and full Debug unit tests, assembleDebug and diff check. Inspect generated host renders. Clearly separate host validation from unperformed device/owner acceptance.

## Validation

- `:app:testDebugUnitTest :app:assembleDebug --console=plain`: **PASS**, 600 tests / 64 suites, zero failures/errors/skips. This includes the 13 new Session UI tests and the existing deadline, presentation-policy, Episode, Rules Engine and Home tests.
- `git diff --check`: PASS. Daily content, shared TodoOverlaySummary, intervention policies and runtime managers have no changes.
- Native host renders inspected under ignored `app/build/reports/todo-session-phase2/`: `countdown-three-light.png`, `five-dark.png`, `empty-light.png`, `one-light.png`, `three-light.png`, `reentry-light.png`, `fallback-dark.png`, `small-large-font-dark.png`, `small-large-font-todos-dark.png`, `landscape-light.png`, `landscape-large-font-dark.png`, `daily-unchanged-light.png`.
- Narrow portrait fixture: 320 × 480dp at 2× font scale; landscape fixture: 800 × 360dp at normal and 2× font scale. Actions stay visible while information scrolls. Countdown is visible initially; text can be scrolled to reach todos. Test requests use synthetic deadlines only inside host tests; production timing parameters are unchanged.
- Host tests exercise actual Compose UI and callbacks but do not verify real WindowManager attachment, HOME execution or vendor rotation/rendering. Device operations: none. Owner accepted Phase 2 when requesting Phase 3 and authorized its standalone checkpoint commit.
- No push/merge/tag/Release/version changes. Phase 3 is a separate development step.
