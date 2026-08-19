# NullWrist 0.8.12

## Feedback fixes

- Fixed Android launcher icon registration by moving the launcher icon to proper mipmap resources and adding adaptive and round icon definitions.
- Hardened stale QEMU cleanup for OEM Android builds that restrict `/proc/<pid>/cmdline` access.
- NullWrist now refuses to start a second QEMU if an old same-app QEMU cannot be terminated safely.
- After repeated runtime failures, NullWrist rebuilds only PebbleOS SPI/AppDB state from the bundled known-good image while preserving the Android PBW library.
- Automatic recovery is now bounded: after five consecutive failures it pauses instead of rebooting PebbleOS forever. Reopening NullWrist or explicitly selecting/restarting a watchface retries cleanly.
- Version code 29; version name 0.8.12.
