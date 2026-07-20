# Pebblehertz

Pebble Time watchfaces running natively on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display is a pixel-art watchface locker with real previews, `.pbw` import, selection and rear-display preview.
- The selected Pebble Time face runs inside native ARM64 QEMU and switches without rebooting the emulator.
- The locker distinguishes a UI selection from a face actually acknowledged by PebbleOS: `QUEUED` becomes `ACTIVE / ON AIR` only after runtime activation.
- Bundled watchfaces are preinstalled in persistent Basalt SPI flash; imported PBWs are installed once and retained.
- A foreground service keeps PebbleOS alive independently from the main Activity.
- When the app opens in the Titan 2 compact rear window, it routes directly to the fullscreen Pebble framebuffer.
- Rear mode consumes touch and generic motion input, blocks Back/predictive Back and requests full-window system-gesture exclusion.
- Rear-mode detection uses both Android `displayId` and actual window pixel bounds because Titan firmware can expose the rear screen as display 0.

## Current state

Pebblehertz 0.8.4 focuses on native runtime performance, battery control and reliable watchface switching.

Validated in CI:

- native AArch64 TCG Core Devices Pebble QEMU;
- Pebble Time/Basalt-only machine registration;
- official Pebble SDK 4.17 Basalt firmware;
- persistent 16 MB SPI flash and 704 KB micro-flash;
- real `144×168` PebbleOS framebuffer support;
- FIFO-driven framebuffer events with a low-frequency safety fallback;
- automatic PBW installation through BlobDB/AppFetch/PutBytes;
- AppRunState switching confirmed by a real new framebuffer generation;
- failed imported-PBW rollback without discarding the running QEMU process;
- asynchronous thumbnail capture after activation;
- always-on foreground runtime lifecycle;
- user-configurable 24-hour PebbleOS freeze schedule;
- charging override and below-15% minute-refresh battery saver;
- sixteen real QEMU-rendered bundled previews.

### 0.8.4 performance and power

The Android QEMU build uses native AArch64 TCG instead of the TCG interpreter. The production runtime no longer opens a diagnostic serial console or continuously writes QEMU logs.

The rear display waits for FIFO frame events from QEMU instead of polling the framebuffer every 50–250 ms. Pixel conversion runs outside the Android main thread, with a one-second sequence check retained as a recovery fallback.

PebbleOS can be frozen on a user-defined `HH:mm` schedule. Charging always keeps the emulator active. Below 15% battery, PebbleOS wakes around the minute boundary, refreshes the face and freezes again.

### 0.8.4 watchface switching

A selected watchface becomes active only after PebbleOS produces a new framebuffer generation. Thumbnail capture is then scheduled separately, so it no longer delays the active state.

If an imported PBW fails to install or launch, Pebblehertz relaunches the previous face without destroying the QEMU process. Per-chunk Android UI and notification progress text has been removed; the PebbleOS loading strip remains visible on the rear screen.

### 0.8.1 runtime repair

PebbleOS readiness is determined by the actual phone-protocol handshake rather than by searching a diagnostic serial stream for a human-readable boot phrase.

A failed start stops and detaches the failed QEMU instance. A later app start or watchface selection creates a clean runtime while retaining the persistent SPI flash. Normal watchface changes use AppRunState without rebooting QEMU.

The WebView/WebAssembly route was tested and rejected because Titan 2 WebView reported `crossOriginIsolated=false`, preventing the pthread-enabled QEMU build from obtaining `SharedArrayBuffer`. Pebblehertz therefore uses a native Android QEMU process and a file-backed framebuffer.

## Bundled watchfaces

The application ships with these pinned PBW packages, preinstalled in the packaged SPI image:

- Big Shadow 2.00.5
- Nyan Cat 8.9
- Pip Boy 100 5.4
- Modern Watchface 3.1.1
- Mario Time 3.41
- 91 Dub 4.0 version 4.21
- polvtorogo 0.1.1
- Enigma 1.1
- Rosewright A 5.1.0
- Electronika 5 1.0
- Darth Time 4.0
- Metro Watch 1.1
- Starfield Smooth 1.0.0
- CMD Time Typed 1.1
- Omega Seamaster 007 1.1
- Studio Clock 6.03

Source pages and SHA-256 hashes are recorded in [`bundled-watchfaces.json`](bundled-watchfaces.json) and [`THIRD_PARTY_WATCHFACES.md`](THIRD_PARTY_WATCHFACES.md).

## Reproducible assets

Fetch pinned PBWs:

```bash
python3 scripts/fetch_bundled_watchfaces.py
```

Fetch official Basalt firmware:

```bash
PEBBLE_SDK_VERSION=4.17 python3 scripts/fetch_pebble_basalt_firmware.py
```

Build Basalt-only Android QEMU:

```bash
bash scripts/build_pebble_qemu_android.sh
```

Build host QEMU, preinstall all bundled faces and render real preview PNGs:

```bash
bash scripts/build_pebble_qemu_host.sh
python3 scripts/preseed_basalt_flash.py --help
python3 scripts/generate_watchface_thumbnails.py --help
```

Generated previews live in `app/src/main/assets/watchface-thumbnails/` and use the watchface UUID as the filename.

## Development stack

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- Android NDK 28
- CMake 3.22.1
- compile/target SDK 36
- minimum SDK 28
- arm64-v8a runtime target

Build locally with:

```bash
python3 scripts/fetch_bundled_watchfaces.py
PEBBLE_SDK_VERSION=4.17 python3 scripts/fetch_pebble_basalt_firmware.py
gradle :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Physical-device validation for 0.8.4

1. Confirm native-TCG QEMU boots on Titan 2 and compare CPU/battery use against 0.8.3.
2. Switch all sixteen bundled faces and confirm each reaches `ACTIVE / ON AIR`.
3. Import several PBWs and verify activation, asynchronous thumbnails and failed-PBW rollback.
4. Verify the configured overnight freeze and resume times.
5. Verify charging override and below-15% minute refresh behavior.
6. Verify static, seconds and animated faces with event-driven framebuffer delivery.
7. Confirm fullscreen and input locking on the rear display remain unchanged.

Rollback point: `backup/pebblehertz-0.8.0-before-runtime-fix`.
