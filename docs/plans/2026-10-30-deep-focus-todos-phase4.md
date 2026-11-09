# Deep Focus Todos Phase 4 Plan

**Goal:** Display all unfinished todos in the existing active Deep Focus UI for both manual and delayed starts.

**Architecture:** Activity's existing Deep ViewModel exposes TodoRepository.dayState; the overlay supplies the same application-scoped TodoRuntime repository flow. A shared lifecycle-aware binding passes the complete unfinished list to DeepFocusContent. No summary truncation, extra database query, task mutations or start-source branching.

**Spec:** Owner's Phase 4 request in this chat. Phase 3 remains uncommitted and not owner device-accepted; preserve its scheduler, receipt, cancellation, permissions and UI logic.

## Implementation

- [x] Add `ui/focus/DeepFocusTodoCard.kt`: full read-only list with stable IDs, wrapping titles, unfinished count and a viewport-relative list height cap. Empty list hides the card.
- [x] Update `DeepFocusScreen.kt`: shared lifecycle-aware todo binding; active layout keeps countdown/guidance/todos/session details and the existing hold control. Scroll information separately from the hold control; adapt landscape layout. Completed/inactive UI, permission actions and hold/confirmation behavior stay intact.
- [x] Update `DeepFocusViewModel.kt` / `MainActivity.kt` only to inject/expose the existing todo day-state flow. Preserve all Phase 3 code.
- [x] Update `DeepFocusOverlayController.kt` only to supply the full flow to the same binding; retain current owner, window and disposal lifecycle.
- [x] Add Debug host Compose tests for 0/1/3/10 tasks, carry-over/completion/reactivity, long titles, small/large-font and landscape scrolling, countdown, short/three-second holds, confirmation, completed state, permission guidance and independently owned ComposeView subscriptions/disposal.
- [x] Run related/full Debug unit tests, assembleDebug and diff check; inspect light/dark/empty/small host renders. Compare Phase 3 text baseline and report permitted DI-only changes separately.

## Boundaries

- feat/todo-experience only; no stage/commit/push/merge/master/version/tag/Release or installation/device operation.
- Do not change Phase 3 scheduling, Deep Focus state/timing/enforcement/notifications/exemptions, ordinary Focus, Session/Daily rules, 04:00 boundary, Todo repository/database/reminders or themes.
- Host view attachment/rendering is not acceptance of a real TYPE_APPLICATION_OVERLAY or vendor behavior. Phase 3 and 4 await joint owner device acceptance.

## Results

- Full `:app:testDebugUnitTest :app:assembleDebug --console=plain`: **PASS**, 636 tests / 66 suites, zero failures/errors/skips. This includes the 14 new Deep Focus UI tests and all 13 Phase 3 scheduling tests. `git diff --check`: PASS.
- Data path: TodoRuntime's existing TodoRepository.dayState → Activity ViewModel or overlay → shared DeepFocusTodoContent lifecycle collector → DeepFocusContent → complete read-only card. TodoDayState.unfinished preserves carried/today ordering; no summary-provider truncation, extra query, database change or task mutation UI.
- Portrait information scrolls separately from the hold control. Landscape uses an information column and the existing hold control at the side. The task list has a viewport-proportional height cap (portrait 28%, landscape 45%, maximum 260dp) and stable todo ID keys; small lists shrink to their contents.
- Native rendering exposed clipped hold-label text at 2× font scale. Only its visual dimensions now scale from 132dp up to 198dp; pointer-input handling, animation, the 3,000ms threshold and the second confirmation remain unchanged. Exact gesture-block comparison and host short-/long-press checks passed.
- Verified completion removal and 04:00 carry-over with a real TodoRepository and in-memory fake DAO; no repository, day boundary or reminder code changed. Live flow updates change the guidance/card without restarting the countdown.
- Activity composition removal and independent ComposeView owner destruction both release their todo subscriptions. The independent host test uses an Activity window and the production OverlayComposeHostOwner/shared binding; it does **not** validate TYPE_APPLICATION_OVERLAY permission or vendor behavior.
- Inspected ignored host renders in `app/build/reports/deep-focus-todos-phase4/`: `deep-three-light.png`, `deep-ten-dark.png`, `deep-empty-light.png`, `deep-small-large-font-dark.png`, `deep-small-todos-dark.png`, `deep-landscape-dark.png`. Small-screen renders use a constrained test viewport inside the host window.
- Phase 3 preservation: 13 of its 15 files match the start-of-task text exactly. MainActivity and DeepFocusViewModel differ only by TodoRepository injection/exposure; all original Phase 3 methods are retained. No scheduler, receipt, cancellation, confirmation/three-button UI or timing change.
- Phase 4 file scope: DeepFocusScreen.kt, DeepFocusTodoCard.kt (new), DeepFocusOverlayController.kt, DeepFocusViewModel.kt, MainActivity.kt, DeepFocusTodosUiTest.kt (new), and this plan (new).
- No installation/device command, Release build, version change, stage/commit/push/merge/tag or later feature work. Phase 3/4 remain uncommitted on feat/todo-experience. Joint real-device validation and owner acceptance remain pending.
