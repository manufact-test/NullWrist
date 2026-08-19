# NullWrist 0.8.12 feedback test plan

## Launcher icon
1. Clean-install the APK on Titan 2.
2. Confirm the package installer and launcher show the actual NullWrist watch/null glyph, never the generic Android robot fallback.
3. Confirm the dedicated raster launcher resource remains sharp and correctly scaled on the stock Titan 2 launcher.
4. Confirm the NullWrist icon also appears correctly in Settings > Apps.
5. Update over 0.8.11 and confirm the icon refreshes without clearing app data where signing permits an in-place update.

## Runtime recovery
1. Run the selected face continuously for at least one hour.
2. Reopen/close the main and rear Activities repeatedly; QEMU must not restart when healthy.
3. Kill the Android parent process abruptly where possible, then reopen NullWrist and verify only one QEMU owns the runtime files.
4. Repeat recovery on the stock Titan 2 ROM where `/proc/<pid>/cmdline` may be restricted.
5. Force a native QEMU failure and confirm exponential retries still occur.
6. Force two consecutive failures and confirm PebbleOS internal SPI/AppDB state is rebuilt without deleting imported PBW files.
7. Force five consecutive failures and confirm recovery pauses instead of entering an endless PebbleOS reboot loop.
8. Reopen NullWrist after the pause and confirm a fresh retry starts.
9. Select another watchface after the pause and confirm the recovery circuit reopens and the new selection can start.
10. Reboot the phone and confirm BOOT_COMPLETED restoration still works.
