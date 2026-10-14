# Final UI verification — 2026-10-14

Reference: the privately retained approved dark design. Its branded illustrative image is not distributed in this public candidate; see [public UI asset policy](../ui/README.md). Device: vivo X200s / Android 16; package `com.example.timemanager`. Raw device captures remain outside Git. This historical validation describes the implemented UI, not a new visual test during migration.

## Implementation boundary

Approved fixed lavender/charcoal palette with system light/dark support; Chinese numeric/unit hierarchy and shared icons. Home uses 今日已使用, compact mode entries, usage before the two-item todo preview and no historical summary. Detail uses a single base+extension=effective relationship, edit/read-only rows and real seven-day history. Statistics shows actual dates, managed-app totals, an unfinished-day average note and rankings without numeric percentages; overview comparison is removed, detail comparison retained.

No Service, AppTimer, UsageStats, quota algorithm/storage, Session, Overlay, Room schema or 04:00 changes were part of that UI task. Detail consumes existing history Flow/calculation. Design sample numbers never replaced user configuration or real data.

## Host verification at that date

**125 Debug JVM tests**, zero failures/errors; Debug and Release builds succeeded; whitespace checks passed. Four duration-display tests were added. These results do not substitute for visual validation.

## Device checks

Dark mode, normal font scale and portrait were checked after successful data-preserving updates. Installation completed and at least one minute elapsed before subsequent UI operations.

| Surface | Recorded observation |
| --- | --- |
| Home | Summary hierarchy, compact modes, actual managed usage above compact todos, no usage percentages/history card. |
| Detail | Real usage/effective allowance, base+extension relation, editable first reminder, readonly status, real history and visible low-emphasis removal. |
| Statistics | 7-/30-day selection, real ranges, cumulative totals, unfinished-day note, daily bars and no numeric ranking shares. |
| Dialogs | Existing quota/reminder fields prefilled from saved settings; opened and cancelled without saving. |
| Navigation | Home→Detail→History→Detail→Home; statistics period switch and permission-page return. |
| Permissions/monitor | Existing granted state was accurately reflected; foreground service and notification ID 1001 remained active. |

Earlier excess spacing/wrapped units were adjusted and the final layout rechecked. One mistimed capture remained on a previous page and contained an unrelated external notification; it was not treated as valid statistics evidence and is not published. Actual user totals, task text and managed-app names are deliberately not reproduced here.

## Limits and closeout

Normal timing was retained; no settings were saved merely to match the mockup. Final updated code remained installed, with existing user data intact. No uninstall, clear-data, test helper or connected-test cleanup was used; temporary app-specific screenshots were handled by exact path.

Full Light/large-font/landscape coverage, removal/save mutations and another long intervention regression were not part of this UI acceptance. Real Android system bars, scrolling and real data appropriately differ from the design illustration.
