# Emery Android device-lab boot experiment / 2026-10-21

This release intentionally has two build flavours by **artifacts**, not by product claims:
- `time2-android.yml`: no firmware or native QEMU; input preview only; signed debug APK.
- `time2-device-lab.yml`: imports independently validated development assets from verified upstream Linux firmware and ARM64-QEMU GitHub Actions, assembles a debug APK for physical Titan2 testing. **There is no successful on-device boot evidence yet.**

The lab APK has a distinct package ID from legacy NullWrist. No root or extra permissions. It launches `pebble-emery` with an actual Cortex-M33 ELF and persistent private SPI, listens for authenticated private UNIX QMP socket and versioned ARGB8888 frames. The worker owns the one process and shuts it down on Activity destruction. LCD/touch tested in official desktop QEMU; Android route remains experimental.

Artifacts are verified against SHA256 manifests from each CI run before being staged on phone. QEMU ELF executable remains in nativeLibraryDir, with useLegacyPackaging; no other application data or older Basalt firmware is touched. If the lab image cannot boot, show a clear status and review `files/time2-emery/emery-qemu.log` using adb from the developer build.

## Manual device smoke (do not skip)
1. Install the **lab** APK alongside legacy 0.8.12; verify neither overwrites the other.
2. Disable no protections until needed. Use Titan2 SubScreen launcher to start the new app; inspect screenshot of actual boot, QMP handshake and frame sequence.
3. Open launcher using virtual OK, back to face, swipe and tap settings; verify touch calibration across all corners (410x502 physical).
4. Switch main/rear screens, unlock/lock and observe whether Android kills foreground activity. Never claim persistent background support yet.
5. Check log after closing: child QEMU dead and private QMP socket removed.
6. Restart twice and check SPI app/data persistence. Test separate legacy 0.8.12 on the same phone.
7. Report device model/build, Android API, logs, observations, CPU and battery impact.

No external PBW installation or real Android notification integration is wired at this stage. Those remain M3/M4.
