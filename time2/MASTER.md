# NullWrist Time 2 — implementation plan / 2026-10-21

**Product**: Run real Pebble Time 2-class Emery PebbleOS on Unihertz Titan 2 rear display (200 x 228 emulated LCD / 410 x 502 physical LCD), touch, four virtual buttons, watchfaces and watchapps. No root.

**Baseline**: `manufact-test/NullWrist` main 71fd6e5e... (v0.8.12). Never change `app/`, prior tags/releases, APK signing, Basalt firmware/SPI or existing user data. New Gradle application/module `time2/app` and new app ID. Existing app can co-exist on one phone; avoid simultaneously running both QEMU services at full CPU when runtime arrives.

## Definition of done — *not yet reached*
On real Android 16 Titan 2, launch Time2 from SubScreen; boot real PebbleOS Emery to authentic watchface; access built-in launcher/settings; run imported Emery watchapp plus 2 watchfaces; receive notification and current music metadata/control with consent; navigate touch and four virtual buttons; survive lock/unlock, screen flip, reboot, wireless loss and process recovery; no hangs or sustained overheating; old NullWrist stays unchanged.

## Architecture (new implementation, not a fork of old Java classes)
1. **Host Android shell** — MainActivity, RearActivity, 5x7 bitmap wordmark, privacy/diagnostics.
2. **ViewportMapper** — exact 200x228 frame letterboxing to arbitrary surface, bounds-check and reverse touch mapping. DONE: unit tests.
3. **Emery PBW library** — private files, UUID, platform/manifest verification, basic watchapp/watchface metadata; content-size cap. DONE: scan/import. TODO: per-file hashes, install queue, uninstall, SDK binary compatibility checks and sandbox.
4. **Emery QEMU backend** — full Cortex-M33 qemu_emery firmware; cross-compile qemu for Android arm64, pin hashes, support independent persistent flash and boot images; no bundled emulator yet. TODO.
5. **Display transport** — versioned frame shared memory or socket with sequence counter and renderer; no frames provided today. TODO.
6. **Input backend** — calibrate QMP touch down/move/up, drag, cancellation; virtual Back/Up/Select/Down; serial event queue. UI mapping/probe DONE, backend TODO.
7. **Package execution** — Pebble protocol install/app-start/stop (BlobDB/AppFetch/PutBytes), preserve UUID state, reboot-safe AppDB. TODO.
8. **Companion** — explicit consent, local Android NotificationListener, MediaSession, optional location/weather/calendar, PebbleKit/AppMessage, per-PBW grants and audits. TODO.
9. **Reliability** — one process, bounded watchdog, atomic flash persistence, CPU/thermal/battery tests, user-controlled always-on. TODO.
10. **Distribution** — proper independent launcher raster icon, signing/release CI, license audit for QEMU dependencies, PebbleOS firmware (Apache-2.0), mobileapp GPL-3.0 (don't copy blindly) and bundled third-party PBWs. TODO.

## Required milestone gates
- M0 (desktop): pin PebbleOS/SDK/QEMU SHA; reproduce `pbl configure --board qemu_emery; pbl build; pbl qemu`; real launcher; QMP button/touch; install at least one emulator-compatible PBW; logs, screenshots and hashes.
- M1 (Android): cross-compile official-compatible QEMU Cortex-M33/peripheral machine to NDK ARM64, build firmware, maintain separate image state; boot real OS and read genuine 200x228 frames; Android 16 proof.
- M2 (UX): connect SurfaceView with frame transport and real touch via QMP, virtual buttons; main/rear screen switching; session state.
- M3 (packages): install/launch/kill face+app via Pebble protocol; maintain AppDB; restart preservation.
- M4 (phone services): add opt-in notification, music, calendar, weather, PebbleKit JS one by one; test exact permission behavior.
- M5 (release): regression tests, repeated 30-minute sessions, thermal, battery, user docs, independent signed APK.

Do not claim a functional Emery runtime until M1-M3 are demonstrated; do not call the new app 1.0 stable before M5.

## Risk decisions
- The old Basalt QEMU is patched to DISABLE every other QEMU machine. It cannot simply be retargeted by changing dimensions. New emulator needs new executable/machine and often different framebuffer format and protocol.
- Pebble Time 2 hardware differs from the QEMU Emery board: this is **platform firmware emulation**, not electrically exact virtual hardware. Physical HRM, BLE controller, mic and other drivers require optional adapters.
- App or watchface PBW may require phone JS and permissions; native emulator alone does not guarantee all third-party apps work.
- Android 16 rear-screen navigation/gesture blocking must be tested on device; no assumption that Android gestures can always be intercepted.
- GPL-3.0 Android companion source is useful protocol reference but not automatically acceptable for reuse without correct license compliance.

## Established QEMU device names (source-verified 2026-10-21)
Core Devices QEMU `hw/arm/pebble_generic.c` defines `-machine pebble-emery` / `cortex-m33`, unlike the firmware *build-board name* `qemu_emery`. Touch is QMP `input-send-event` with ABS X/Y and BTN left; pebble_touch.c scales from QEMU absolute input range. `EmeryQmpCommands` builds serialized messages for the future transport; input is still diagnostic-only. Source pinning: `upstream.lock.json`.

## Acceptance evidence 
Store CI links, source hashes, signed APK hashes, physical-device logs and reproduction steps in this directory as milestones complete. No build success alone proves PebbleOS actually boots on Titan 2.

## Native-build experiment (2026-10-21)
Isolated script `time2/scripts/build_emery_qemu_android.sh` uses pinned QEMU `8a0a7c6`, Android NDK 28, ARM64 API35, compile-time framebuffer v2 exporting *final rendered ARGB8888* from generic Emery display. Adds Android shm shim without touching Basalt. CI: `.github/workflows/time2-emery-native.yml`. **Not ready for distribution until it passes and the binary boots real firmware on-device.**

## M0 desktop outcome — verified 2026-10-21
M0 firmware boot and controls sub-gate **PASS**: GitHub Actions run `39600331988` compiled pinned `qemu_emery` firmware and executed **four upstream integration cases**: launcher opens, back returns home, swipe scrolls, touch opens a menu item. Result: **4 passed in 87.36s**. This is native Linux QEMU, NOT Android. Full original M0 still requires installation and launch of a third-party Emery PBW, tracked separately. Do not mark M1 (Android native boot) as complete based on this result.

## M1 Android builder first-attempt notes
The initial experimental workflow `39601332647` failed before starting compilation because the generated shell script contained malformed duplicated trailing lines. The old Android project and its artifacts were not affected. Fixed in Time2 branch; future runs must pass `bash -n` before toolchain installation. Native proof is still pending.
