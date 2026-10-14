# Open validation and release work

Migrated from the retired task checklist on 2026-10-14; completed or superseded items were not copied as open work. These are recommended issue titles, not claims that GitHub Issues were created.

- [ ] **Validate the full permission-revocation matrix.** Usage / Overlay / Notification revocation, revocation while a reminder is visible, first-reminder HOME capture and the notice's View action. Existing key Accessibility-off/on paths are already verified; do not repeat them as missing implementation.
- [ ] **Complete responsive UI validation.** Light mode, large fonts, real landscape, independent dialogs and Session/Daily todo summaries. A locked-portrait target receiving a rotation command is not full landscape evidence.
- [ ] **Complete re-entry and priority device regression.** Target notification entry, Daily/Focus/Deep arbitration and daily-extension interaction. Same-stage gate consumption, Continue/Stay Home, next +8-minute stage and genuine five-minute idle reset already have device evidence.
- [ ] **Validate real background todo stages.** Actual 04:00 rollover, 20:00 / 23:00 delivery, notification tap and OS delay behavior. Deterministic host scheduling tests do not replace these observations.
- [ ] **Validate incoming-call Deep Focus exceptions.** Real incoming/in-call UI, overlay suspension and resumption after call end. Emergency UI checks do not prove incoming-call compatibility.
- [ ] **Broaden Android/vendor coverage.** Background survival, event retention, system launch prompts and permission settings beyond the primary vivo X200s / Android 16 device. System-restricted Debug Logcat remains an evidence limitation, not proof of missing events.
- [ ] **Complete release distribution review.** Choose release metadata, signing and store permission disclosures (app visibility, Accessibility and special-use foreground service). Confirm third-party visual-asset provenance and Android backup/privacy policy before distributing a public binary.

Follow [DEVICE_SAFETY.md](../DEVICE_SAFETY.md). None of these items authorizes uninstall, clear-data, changing other apps or running unsafe instrumentation on the user's phone.
