# Pebblehertz 0.8.10 Stage 1 test plan

This matrix covers the Reliable Always-On stabilization pass before PR #9 is merged.

## Automated checks

- Unit tests for runtime-mode storage and unknown-value fallback.
- Unit tests for Schedule Sleep selection queueing and wake behavior.
- Unit tests for task-removal recovery policy.
- Android debug compilation and packaging.
- Native ARM64 QEMU and Basalt firmware asset verification.

## Installation and migration

1. Install over signed 0.8.9 without clearing app data.
2. Confirm the redesigned runtime dialog appears once.
3. Confirm Reliable Always-On is visually marked RECOMMENDED and selected by default.
4. Close the dialog and reopen it from the Runtime Reliability card.
5. Confirm a fresh install starts in Reliable mode without an upgrade-only dialog.
6. Confirm PBWs, selected face, Night Mode hours and thumbnails survive the update.

## Runtime mode switching

1. Switch Reliable → Silent → Reliable while a bundled face is active.
2. Confirm QEMU does not reboot during either switch.
3. Confirm the current face remains ACTIVE / ON AIR.
4. Repeat while an imported standalone PBW is active.
5. Reopen the app and confirm the chosen mode persists.

## Foreground-service presence

1. Confirm the service notification has no buttons or actions.
2. Confirm it has no sound, vibration, timestamp or badge.
3. Confirm Pebblehertz no longer requests notification permission.
4. Confirm Reliable mode still appears in Android Active apps / Task Manager as required by Android.
5. Confirm Silent mode removes foreground-service presence.

## Clear all and Activity lifecycle

1. Start a face in Reliable mode and close only the main Activity with Back.
2. Confirm the rear face continues without a QEMU restart.
3. Open Recents and tap Clear all.
4. Confirm the face remains active or recovers automatically without opening Pebblehertz manually.
5. Repeat while the phone is locked, then unlock and inspect the rear display.
6. Repeat while charging and while unplugged.
7. Open the main Activity after recovery and confirm the exact previous UUID is ACTIVE / ON AIR.
8. Repeat in Silent mode and confirm any failure is communicated as the documented reliability trade-off.

Android Task Manager Stop, App info Force stop and OEM App blocker actions are terminal system actions, not Clear all. The app must not attempt to bypass them.

## Schedule Sleep

1. Set a sleep interval that includes the current minute.
2. Confirm PebbleOS enters SCHEDULE SLEEP once without repeated pause commands.
3. Leave the app open for at least three policy ticks and confirm no freeze, reboot or status churn.
4. Select one different face during sleep and confirm it displays QUEUED FOR WAKE.
5. Select several faces rapidly; only the final selection should remain queued.
6. Select the already active face again and confirm the queue clears.
7. Confirm the displayed rear framebuffer remains unchanged during sleep.
8. Connect the charger and confirm PebbleOS resumes once and applies the final queued face once.
9. Disconnect the charger while still inside the sleep interval and confirm PebbleOS returns to sleep once.
10. Let the schedule end normally and confirm the final queued face is applied once.
11. Repeat with bundled and imported standalone PBWs.
12. Change the schedule while a face is queued and confirm the resulting power state remains coherent.

## Low-battery behavior

1. Below 15%, confirm minute-pulse mode still resumes and pauses through the serialized runtime executor.
2. Change faces during an awake pulse and confirm no concurrent pause interrupts installation.
3. Connect a charger and confirm continuous running replaces minute-pulse mode.
4. Disconnect the charger and confirm the correct Night Mode or low-battery policy resumes.

## Watchface operations

1. Rapidly switch among at least five bundled faces outside Schedule Sleep.
2. Confirm only the exact AppRunState UUID reaches ACTIVE / ON AIR.
3. Import a standalone PBW and activate it.
4. Import the same UUID again and confirm duplicate protection.
5. Delete a non-active imported PBW.
6. Delete the active face and confirm a valid replacement is selected without an uncontrolled QEMU reboot.
7. Attempt to activate a failing PBW and confirm the previous face is restored.
8. Confirm thumbnail capture does not run against a frozen or replaced runtime.

## Long-running smoke test

1. Run Reliable mode for several hours with the main Activity closed.
2. Include at least one lock/unlock cycle, Clear all, charging transition, face switch and Schedule Sleep transition.
3. Confirm there is no notification alert, runaway restart loop, permanent STARTING state or stale queued badge.
