# Accepted Session fix: stable update and Git handoff

**Goal:** Install the owner-accepted source into the daily app without tests, then prepare (not execute) the final Git operations.

**Architecture:** Rebuild the release variant with unchanged version metadata and production timing. Reuse the existing compatible signing identity for a local `install -r` update; do not configure a new public-release key. Git review stays on the existing branch without staging, committing, merging or pushing.

**Spec:** Owner's 2026-10-17 acceptance and explicit stable-update authorization in this chat. No device functional testing.

## Steps

- [x] Preserve all current work; reuse the successful 155-test source state. Build `:app:assembleRelease` only; no new timing/configuration code or version bump.
- [x] Compare the retained previously installed APK signer with the existing local identity by direct certificate equality; sign a separately named ignored artifact. Verify SDK package/name, release flags and signature.
- [x] Confirm device and installed daily package; remind owner and cover-install `com.example.timemanager` using `install -r` only. Require Success and a 60-second wait. Do not launch/test, uninstall, clear data or touch Dev.
- [x] Record owner acceptance and deployment boundary. Review diff/untracked/private artifacts, cached validation and Git identity; prepare exact candidate paths and a commit proposal in `docs/testing/2026-10-17-stable-update-git-handoff.md`.
- [x] Stop before Git staging, commit, merge or push; wait for owner confirmation. Do not auto-publish or tag.

Follow-up: the owner then explicitly authorized the proposed two local commits and confirmed the noreply identity. Source commit: `415cc5b`; documentation is recorded in the separate commit containing this follow-up. Push/master integration remain unauthorized. See [final handoff](../testing/2026-10-17-stable-update-git-handoff.md).
