# NullWrist 0.8.12

## Feedback fixes

- Fixed the NullWrist application icon on the Unihertz Titan 2 launcher and package installer by switching the primary launcher icon to a dedicated 512×512 raster mipmap resource with a new resource ID.
- Hardened stale QEMU cleanup on OEM Android builds that restrict `/proc/<pid>/cmdline` access.
- NullWrist now refuses to start a second QEMU if an orphaned same-app QEMU cannot be terminated safely.
- After repeated runtime failures, NullWrist rebuilds only PebbleOS SPI/AppDB state from the bundled known-good image while preserving imported PBW files in the Android library.
- Automatic recovery is now bounded: after five consecutive failures it pauses instead of rebooting PebbleOS forever. Reopening NullWrist or explicitly selecting/restarting a watchface retries cleanly.
- Version code 30; version name 0.8.12.
