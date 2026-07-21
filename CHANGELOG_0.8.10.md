# Pebblehertz 0.8.10

## Stage 1 — Reliable Always-On

- Reliable Always-On is now the default runtime mode.
- PebbleOS runs as an Android `specialUse` foreground service in Reliable mode.
- The foreground-service notification is minimal, silent and contains no buttons or actions.
- Pebblehertz no longer requests notification permission; Android may still expose the service through Active apps as required by the platform.
- Silent Mode remains available as an optional less-reliable alternative.
- Runtime mode can be changed without restarting QEMU or reinstalling the app.
- Existing 0.8.9 users receive a one-time explanation of the new default.
- New installations begin in Reliable mode automatically.
- Android 14+ foreground-service type and permission declarations are included.
- Runtime mode selection uses a custom Pebblehertz-styled dialog with Reliable visually marked as RECOMMENDED.
- Reliable mode uses `onTaskRemoved` plus a guarded AlarmManager fallback after Titan 2 removes the app task.
- Watchface changes during Schedule Sleep are queued and applied once after wake or charger connection.
- Schedule Sleep sends only one QEMU pause command per sleep interval.
- Runtime launch, watchface switching, battery sync, power policy, pause and resume operations are serialized through one executor.
- Returning to the already active face during Schedule Sleep clears a stale queued selection.

Physical-device validation is tracked in [`STAGE1_TEST_PLAN.md`](STAGE1_TEST_PLAN.md).
