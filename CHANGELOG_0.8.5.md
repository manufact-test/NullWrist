# Pebblehertz 0.8.5

## Command sequencing

- AppRunState commands are acknowledged only when PebbleOS reports the exact requested UUID.
- PBW PutBytes transfers retain the proven 4 ms pacing used by the preseed installer.
- Stale endpoint responses and mismatched PutBytes cookies are rejected.
- An unhealthy phone-protocol socket is reconnected without restarting QEMU.
- A stale installed-watchface registry entry falls back to a real PBW reinstall.
- Deleting the active PBW can switch to a replacement without first reopening the deleted file.

## Thumbnails

- Pending captures are cancelled as soon as a new selection begins.
- Imported thumbnail keys are isolated by stored PBW identity.
- The capture schema is bumped so affected imported covers are recreated.
- The hero preview follows the acknowledged active face.

## Night Mode and preview

- Night Mode uses only `START SLEEP` and `END SLEEP`; there is no enable checkbox.
- The visible frame remains on screen while PebbleOS is frozen during the configured interval.
- Charging overrides Night Mode and keeps PebbleOS running.
- Main-screen preview shows that the phone keyboard Back key exits preview.

## Required Titan 2 validation

1. Switch bundled faces repeatedly and rapidly.
2. Import a PBW, switch to a bundled face, import another PBW, and continue switching.
3. Re-import a different build with the same UUID.
4. Delete the currently active imported face and confirm the replacement launches.
5. Confirm every imported cover belongs to the correct face.
6. Test Night Mode across midnight, charging override, and the preview Back-key hint.
