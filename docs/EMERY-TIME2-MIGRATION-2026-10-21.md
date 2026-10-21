# NullWrist — Pebble Time 2 (Emery) migration feasibility
Date: 2026-10-21
Status: RESEARCH / ARCHITECTURE PROPOSAL. **No Emery Android build, firmware boot, APK or hardware validation has been performed.**
Base: main @ 71fd6e5eba5b36b50168277be82e0eadbbc2a58c (0.8.12 / versionCode 30).

## Goal
Turn the Titan 2 rear touchscreen into an interactive PebbleOS watch (launcher, watchfaces, watchapps, settings, navigation and selected phone services), **without root**. The target platform is Emery, associated with Pebble Time 2 (200 x 228, 64 colours, touch, four buttons). This is OS/platform emulation, NOT bit-exact emulation of the SiFli watch PCB, its BLE radio or every physical sensor.

Non-regression principle: Basalt 0.8.12 remains the stable path, with the same signed package ID and imported watchfaces. Emery is opt-in and independently versioned until verified. Never auto-migrate or overwrite existing Basalt SPI flash.

## Evidence reviewed (21 Oct 2026)
### Existing NullWrist
- README.md, ROADMAP.md, COMPATIBILITY.md and docs/architecture.md still largely describe 0.8.11 and a watchface-only roadmap; app/build.gradle.kts identifies 0.8.12.
- app/src/main/java/com/manufacttest/pebblereardisplay/ui/RearDisplayActivity.java: dispatchTouchEvent and dispatchGenericMotionEvent unconditionally return true. The rear UI is deliberately passive; button keys are intercepted.
- runtime/PebbleQemuProcess.java: WIDTH=144, HEIGHT=168, ASSET_ROOT=pebble/basalt/, on-disk runtime at pebble-runtime/basalt; QEMU machine pebble-snowy-bb, CPU cortex-m4, no interactive QMP monitor; consumes custom framebuffer event files.
- ui/PebbleFramebufferView.java: bitmap and pixel buffers are dimensioned from static PebbleQemuProcess constants; pixels are displayed with aspect-ratio fitting; no input transformation.
- data/PbwParser.java: explicitly rejects PBWs where watchapp.watchface is not true.
- runtime/PebblePbwBundle.java: selects basalt/manifest.json or root manifest.json, not emery/manifest.json.
- runtime/PebbleAppInstaller.java: already supports AppFetch, PutBytes, BlobDB and AppRunState UUID confirmation. This is a valuable protocol base, but needs version / platform verification.
- runtime/PebbleRuntimeService.java: owned lifecycle, heartbeat/watchdog, persistent selection and bounded recovery are reusable but watchface-centric.
- scripts/build_pebble_qemu_android.sh: pins Core Devices QEMU 9ef633fa512105d8bcf16b93322f99660ce9d4ab, patches the framebuffer and removes other machine registrations via patch_qemu_basalt_only.py. Neither patch is evidence that the newer Emery QEMU works on Android.
- Native Android executable (libpebble_qemu_exec.so), stored Basalt flash images and 16 preloaded watchfaces are Basalt-specific.

### Official upstream developments
- PebbleOS includes a qemu_emery **firmware/board build target**, and the official QEMU emulator runs ARM firmware, PBW binaries, display, buttons and touch: https://pebbleos.readthedocs.io/en/latest/development/emulation/emulator.html .
- Firmware build uses `pbl configure --board qemu_emery`, `pbl build` and `pbl qemu`: https://developer.repebble.com/pebbleos/docs/development/qemu/ .
- Emulator control supports QMP / sendkey, touch and swipe; actual QEMU machine string and runtime arguments must be read from the current SDK launcher rather than assuming `-machine qemu_emery`.
- Phone data testing examples (weather, notifications, calendar, music) exist under `pbl feed`: https://pebbleos.readthedocs.io/en/latest/pbl/feed.html .
- The official mobileapp can attach to a desktop emulator through TCP (with an adb-only pairing/debug procedure on Android): https://developer.repebble.com/guides/tools-and-resources/testing-with-the-mobile-app/ . An installed public mobile app is **not** assumed to pair automatically to a separate app's embedded emulator on the same phone.
- Android Pebble companion source (Kotlin Multiplatform, libpebble3, PebbleKit JS) is available: https://github.com/coredevices/mobileapp . Important: mobileapp source is GPL-3.0 or commercial; code reuse requires a deliberate licensing choice and audit.
- PebbleOS firmware is Apache-2.0; QEMU and its dependencies have separate licenses. Check licenses and source/binary notices before distribution.
- Emery = 200 x 228; Titan 2 rear display = 410 x 502. When aspect-fitted fullscreen, emulator pixels scale 2.05x, giving about 410 x 467 with ~17 px spare vertically. Touch mapping must use the *actual* target view bounds/insets/rotation, not guessed screen dimensions.
- Touch capabilities vary by firmware version and application. Preserve four Pebble buttons as virtual controls (Back/Up/Select/Down); do not rely on every system screen being navigable with swipe alone.

## Proposed modular architecture
`
Titan 2 front UI: library / install / permissions / diagnostics
    |
Android foreground PebbleSessionService (single active VM)
    |-- PlatformProfile: BASALT_144x168 or EMERY_200x228
    |-- FirmwareStore: immutable verified images + per-profile persistent SPI
    |-- EmulatorRuntime: isolated QEMU process, lifecycle and bounded recovery
    |-- DisplayTransport: versioned framebuffer/frame events, display mapping
    |-- InputTransport: tap / drag / release via QMP; Back Up Select Down via QMP
    |-- PackageManager: inspect targetPlatforms, choose matching PBW manifest
    |-- PhoneBridge: Pebble Protocol endpoint router (one TCP client owner)
    |     |-- notifications (opt-in NotificationListener)
    |     |-- media (MediaSession / available controls)
    |     |-- calendar / weather (permissioned data)
    |     |-- PebbleKit JS + settings pages (separate sandboxed process/permissions)
    |     +-- optional health / phone motion sensors (explicit opt-in)
    |
Titan 2 rear UI: frame + minimal virtual four-button affordance
`

Only one emulator process should be active. Never allow both PebbleKit and a standalone feed client to own the same protocol port simultaneously. Retain the existing bounded restart rule and idempotent shutdown.

## Implementation gates (in order; no skip)
### M0. Desktop feasibility (not started)
Pin upstream PebbleOS, PebbleOS-SDK and Core Devices QEMU revisions. Build `qemu_emery` firmware on supported Linux host and boot to real launcher. Prove Back/Up/Select/Down, touch (tap/swipe) and installing an Emery PBW through the supported emulator tooling. Capture logs, firmware/QEMU hashes and screenshots. If this fails, DO NOT alter Android runtime.
Acceptance: deterministic documented desktop boot + one watchapp + one watchface + touch + buttons.

### M1. Android ARM64 emulator spike (not started)
Cross-compile appropriate QEMU fork for Android arm64, preserving its Emery peripheral model and input interface; do **not** apply patch_qemu_basalt_only.py. Identify actual machine/cpu/kernel/flash options from supported SDK. Prove firmware starts, runs and emits valid frames on Titan 2 (Android 16), including restart and no-root requirements.
Acceptance: isolated proof executable and repeatable boot-to-launcher without modifying the Basalt package path.

### M2. Generalized display/input and session (not started)
Introduce explicit platform profiles and separate persistent stores. Make renderer allocate buffers from validated runtime frame dimensions (144x168 / 200x228) and support indexed colour/pixel formats negotiated by the transport. Map Android MotionEvent ACTION_DOWN/MOVE/UP/CANCEL to touch via serialized QMP events, with coordinate transform accounting for letterboxing/insets; implement a reliable visible/flyout four-button UI. Android gesture exclusions are best-effort and may not suppress mandatory OS gestures.
Acceptance: stable 30-minute interactive rear-screen session with launcher navigation, long press/drag, and no accidental exit to Android.

### M3. Real app installation and state (not started)
Refactor package metadata to accept both watchfaces and watchapps and to differentiate target platforms and binary variants. Validate zip limits / paths / oversized resources; preserve UUID dedup with appropriate app-type rules. Choose emery/manifest.json for Emery, basalt/manifest.json for Basalt. Check emulator compatibility and any documented legacy Bezel Mode rather than assuming all Basalt-only PBWs run natively on Emery.
Acceptance: fresh launch, switch, uninstall, relaunch and persistence across reboot for >=3 representative Emery apps and >=3 faces; previous 16 Basalt faces still usable under Basalt.

### M4. Phone companion bridge (not started)
Start with an independent endpoint router and reference upstream pbl feeds. Enable one opt-in capability at a time: time/date; notifications; media metadata/control; calendar; weather; then settings / PebbleKit JS, health sensors and location if proven. Official mobileapp/libpebble3 is reference implementation; copying GPLv3 sources into NullWrist is a separate licensing decision. Never grant arbitrary network/notification/location access to every imported PBW by default.
Acceptance: recorded protocol fixtures and end-to-end live Android verification for each capability.

### M5. QA / release (not started)
Keep 0.8.12 stable and existing signing identity. Validate background operation, screen lock, DuraSpeed/OEM process restrictions, rotation, touch when wet/misfires, CPU/memory/heat/battery, persistent flash after force kill, no forked QEMU processes, and offline use. Enforce existing restart cap. Run unit, Android and native CI; manual Titan 2 smoke. Use prerelease version 0.9.x or 1.0.0-beta only after emulator actually works.
Acceptance: tester can use watchface -> launcher -> app -> notification -> media -> return to face without foreground phone UI; no regression on Basalt.

## Product behavior
- Main screen: `Pebble Time` or `Pebble Time 2` engine selection; app & face library; import PBW; privileges per capability; logs/status; status of VM vs active app.
- Rear screen: actual PebbleOS fills available 410x502 area preserving 200x228 aspect ratio; standard tap and drag routed to Pebble touch; four accessible virtual Pebble buttons (do not replace them with unsupported swipe-only navigation).
- Always-on behavior remains a user choice under Android battery/display restrictions; no hidden elevation/root.
- An Android phone is not literally a watch: unsupported BLE watch identity, independent HR sensor, radio timing, microphone, speaker, vibrator and pedometer APIs must be bridged or honestly reported as unavailable.
- Show precise app compatibility instead of misleading "supported" labels.

## Risks / validation
1. New firmware/boot image may require a different QEMU build/ABI/peripheral set than the legacy Android QEMU: **largest blocker**.
2. Display patch currently hooks Basalt-specific graphics: must measure Emirates frame format and event plumbing before reuse.
3. Touch is software/API and firmware dependent. Newly added system gestures vs app-specific touch are distinct.
4. Android secondary display may not deliver all input on OEM launchers; verify on user's actual Titan 2 Android 16.
5. Current watchface persistence is coupled to selected face; launching the OS app launcher must not trigger a watchface re-selection loop.
6. PBW is not Android APK; no claim of universal app compatibility until proven.
7. GPL/QEMU/Apache licensing and distributing third-party firmware/PBW images require dedicated compliance review.
8. README/ROADMAP/COMPATIBILITY still cite 0.8.11 and outdated near-term plans; update only when milestones become implemented.

## Immediate next engineering task
Implement **M0 as an isolated desktop CI spike** on this branch: pin source commits, compile qemu_emery firmware, boot/check menus, test QMP button/touch and emery PBW installation. Do not merge this research document as proof of executable Emery support; successful CI and Titan 2 device evidence are required before claiming compatibility.
