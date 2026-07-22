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
- Battery-optimization exemption is requested immediately on first launch.
- Removed runtime mode selection and Silent mode.
- Active, selected and final remaining watchfaces cannot be deleted.
- Version code 26; version name 0.8.10.
