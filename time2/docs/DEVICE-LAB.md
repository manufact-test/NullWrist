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

## Capturing failures without root / adb
In the Time 2 front-screen application, choose `EXPORT EMERY DIAGNOSTICS` after attempting an Emery boot on the rear display. Android's document picker writes the latest 512 KiB of `emery-qemu.log` to a text file in the location you choose. This avoids broad storage permissions. Send this text log alongside a screenshot/photo of the rear display and whether QMP responded.

### LCD black grid after QMP online — on-device observation
First Titan2 screenshot displays `Emery QMP online; waiting for real LCD pixels`, proving QMP handshake only, not guest firmware boot. User-exported QEMU log contains only SIGTERM from app shutdown; no internal crash trace. Source review found QEMU's GUI refresh timer is registered only when a display listener has `dpy_refresh`, while our emulator uses `-display none`. New lab strategy issues bounded periodic `screendump` QMP commands to force `graphic_hw_update`, reads the **actual** framebuffer v2 exporter afterward, and logs Android-side boot/refresh outcomes separately from native QEMU. Once validated, move to a native no-disk refresh driver.

## First successful physical device evidence (2026-10-22)
Titan2 user reports **"Ура! Работает!"**. Exported Android-side log proves successful QMP session, QEMU screenshot, real ARGB v2 pixels. No QEMU crash seen; SIGTERM is normal Android shutdown. See M1 statement in MASTER. Does not by itself establish button/touch results or a long-running stable session.

## 0.3.0-lab new physical test
1. From front screen, boot on Titan2 rear display; check actual watchface and smooth navigation. Test OK→launcher, BACK→face and swipe/tap.
2. Keep watchface running about 2 minutes, verify animation/time updates, screen switching and no repeated pause/crash. New native headless refresh timer drives display every ~80 ms; QMP screendump is now a slower fallback.
3. Close virtual watch, use **BACK UP EMERY WATCH MEMORY**. Do not uninstall Time2 before saving the backup if its virtual data matters.
4. Install next debug build, use **RESTORE EMERY WATCH MEMORY** and verify settings remain. Uninstalling a debug APK may erase its private flash when signing key changes.
5. If screen is black / touch fails / backup fails, use **EXPORT EMERY DIAGNOSTICS**; it includes native and Android-side logs. No root, adb or external network required.

PBW import now recognizes authentic Emery plus SDK-compatible Basalt/root PBW manifests, but **does not install them into PebbleOS yet**; no app execution claim.

## 0.4.0-lab — first watchface installation attempt
1. On main screen choose **IMPORT EMERY .PBW**, select a PBW watchface; a successful import only puts it into the private Android library.
2. Tap **INSTALL / LAUNCH FACE — [name]** next to that entry. This starts the guest PebbleOS and sends the PBW over QEMU's private UART1 using BlobDB/AppFetch/PutBytes, with visible status.
3. Verify actual face visible. **No assumption:** the first serial bridge attempt may fail; in that case note the last visible status and export **EMERY DIAGNOSTICS** from main screen. These logs include installer errors.
4. For an import error, record the dialog text and (optionally) provide the PBW file; a separate QEMU boot log cannot diagnose a rejected ZIP/UUID/manifest.
5. Old 0.8.12 NullWrist remains a different package. Do not uninstall it.
