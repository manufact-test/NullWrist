# Pebblehertz 0.8.10 stability test plan

The runtime has one operating mode. There are no sleep, charging or low-battery execution branches.

## Automated checks
- Bounded restart-backoff tests.
- Destructive watchface-operation tests.
- PBW parsing and duplicate UUID tests.
- Signed ARM64 release compilation.
- Native QEMU and Basalt firmware asset verification.

## First launch
1. Install over 0.8.9 or an earlier 0.8.10 test build.
2. Confirm Android immediately requests battery-optimization exemption when needed.
3. Return and confirm SYSTEM ACCESS: UNRESTRICTED.
4. Confirm there is no runtime-mode chooser, schedule card or notification permission prompt.

## Continuous runtime
1. Run one face for at least one hour.
2. Lock/unlock and open/close both Activities repeatedly.
3. Use Recents Clear all and confirm the face stays active or recovers.
4. Connect/disconnect charging and confirm nothing changes.
5. Confirm no sleep or battery-saver state appears at any battery level.
6. Confirm the notification is silent, ongoing, button-free and non-interactive.

## Switching
1. Switch normally between bundled faces.
2. Rapidly tap five faces; only the final request should become ACTIVE.
3. Tap the active face repeatedly; QEMU must not reboot.
4. Switch while opening/closing the main Activity.
5. A failed PBW should restore the previous face when the runtime remains healthy.

## Import and deletion
1. Import a valid standalone PBW.
2. Reject duplicate UUID, invalid, cancelled and oversized imports without disrupting the active face.
3. Block deleting the active or selected face.
4. Delete an inactive imported face without touching QEMU.
5. Block deleting the final remaining face.

## Recovery
1. Kill only the launcher Activity.
2. Use Recents Clear all.
3. Simulate native QEMU exit and verify watchdog restart/backoff.
4. Reopen during recovery and confirm no duplicate QEMU.
5. Verify stale PID cleanup before recovery.
6. Android Task Manager Stop, Force stop and OEM App blocker remain terminal actions.
