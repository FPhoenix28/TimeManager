# Calm UI Unification Implementation Plan

**Goal:** Unify the Home, Session reminder and Deep Focus screens while keeping their existing behavior.

**Architecture:** Reuse the Material 3 theme and spacing. Share panel styles and read-only task rows while retaining each screen's existing state and actions.

**Tech stack:** Kotlin, Jetpack Compose Material 3, Robolectric Compose tests.

**Spec:** Owner's visual-unification request.

- [x] Add shared panel tones and use them in the three task cards. Keep the preview limits, full list, callbacks and task order.
- [x] Share the Home usage header across states, simplify app rows, and align Focus and Deep Focus entry cards.
- [x] Restyle the Session countdown and keep confirmation inside the same panel. Preserve all action availability checks and callbacks.
- [x] Keep the Deep Focus countdown above the scrolling information and the hold control outside it. Reuse the existing timer and hold gesture.
- [x] Run Debug tests and build, inspect host renders, and leave the feature branch for owner review.

## Verification

- `:app:testDebugUnitTest :app:assembleDebug --console=plain` passed with 636 tests across 66 suites. `git diff --check` passed.
- Host renders are in the ignored `app/build/reports/` directories `todo-home-phase1`, `todo-session-phase3`, and `deep-focus-todos-phase4`. Review included light, dark, landscape, and constrained viewports with large text.
- Real-device review remains pending for the system overlays and the small-screen scrolling experience.

## Owner review follow-up

- Use one Session confirmation heading and two flat time columns. Keep the existing confirmation callbacks and compact the Session-only spacing.
- Reduce only the Home todo card's vertical padding and preview row spacing.
- Use the Deep Focus list's scroll state for a lightweight footer. Format the end time using the device's clock preference.
- Re-run the Debug suite and generate a separate set of host images for owner review.

The follow-up passed all 638 Debug tests across 66 suites, the Debug build, and the diff check. Fresh host renders are under the ignored `app/build/reports/ui-polish/` directory. The large-font landscape test scrolls to the cancellation instructions and verifies both confirmation actions remain visible. Real-device Overlay review remains pending.
