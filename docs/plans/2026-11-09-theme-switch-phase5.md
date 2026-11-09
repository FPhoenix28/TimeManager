# Theme Switch Phase 5 Implementation Plan

**Goal:** Save an appearance preference and apply it to the Activity and the three independent overlay compositions.

**Architecture:** One Preferences DataStore publishes the existing ThemeMode. Each Compose root uses the same lifecycle-aware theme wrapper. The current permissions destination gains an appearance section and retains its permission actions.

**Tech stack:** Kotlin, Preferences DataStore, Compose Material 3, Robolectric.

**Spec:** Owner's Phase 5 theme-switch request.

- [x] Implement AppearancePreferences and verify default, invalid values and disk persistence.
- [x] Connect the Activity and independent ComposeView roots to the preference without recreating their hosts or changing enforcement state.
- [x] Add three radio rows to Settings and preserve the permission and navigation callbacks.
- [x] Run the Debug suite and build. Render the Settings, Home, Session, confirmation and Deep Focus states in both palettes.

## Results

- 648 tests across 68 suites passed. The Debug build and `git diff --check` passed.
- Host tests dispatched night-mode configuration changes to the Activity view tree and its independently owned ComposeView, verified preference reload from disk, and exercised the four existing permission callbacks at large font scale in landscape.
- All ten host renders are under the ignored `app/build/reports/theme-switch-phase5/` directory.
- Real system overlay configuration dispatch, actual permission screens and cold startup on the phone remain device review items.
