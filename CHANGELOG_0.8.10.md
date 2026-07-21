# Pebblehertz 0.8.10

## Stage 1 — Reliable Always-On

- Reliable Always-On is now the default runtime mode.
- PebbleOS runs as an Android `specialUse` foreground service in Reliable mode.
- The persistent notification is low-priority, silent and includes a Stop action.
- Silent Mode remains available as an optional less-reliable alternative.
- Runtime mode can be changed without restarting QEMU or reinstalling the app.
- Existing 0.8.9 users receive a one-time explanation of the new default.
- New installations begin in Reliable mode automatically.
- Android 14+ foreground-service type and permission declarations are included.

Physical-device validation should cover Clear all, locking and unlocking, closing the main Activity, notification permission denial, the notification Stop action, charging transitions, low-battery mode and repeated switching between Reliable and Silent modes.
