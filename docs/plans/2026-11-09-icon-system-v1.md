# Icon System v1 Integration Plan

**Goal:** Apply the five supplied transparent PNGs to feature labels and the delayed Deep Focus confirmation.

**Architecture:** Copy original PNGs to drawable-nodpi and use a shared untinted Compose Image. Keep navigation and action icons unchanged.

- Wire the Focus and Deep Focus entries, Home todo header, and Statistics header.
- Replace the confirmation artwork. Show the Session icon in its header only with sufficient vertical room and standard font scale.
- Verify resource decoding, Debug APK contents, and host Compose renders for both palettes and constrained layouts.
