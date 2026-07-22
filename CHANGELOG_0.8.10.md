# Pebblehertz 0.8.10

## Stability-first runtime

- One continuous always-on runtime.
- Removed Night Mode, sleep scheduling, charger overrides, battery forwarding and low-battery pulse mode.
- Serialized all normal QEMU protocol work through one executor.
- Coalesced rapid watchface taps so only the latest request is applied.
- Added native-process watchdog recovery with bounded exponential backoff.
- Added guarded recovery after Recents task removal.
- Added stale orphaned QEMU PID cleanup before startup.
- Kept the foreground notification silent, button-free and non-interactive.
- Notification access and battery-optimization exemption are requested in sequence on first launch.
- Added boot/update restoration through `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- Added a bounded 90-second partial wake lock only while QEMU starts or recovers.
- Replaced the large stability explanation with a compact, color-coded action card that opens the missing setting.
- Removed runtime mode selection and Silent mode.
- Active, selected and final remaining watchfaces cannot be deleted.
- Version code 27; version name 0.8.10.
