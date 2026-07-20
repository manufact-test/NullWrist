# Pebblehertz

Pebble Time watchfaces running natively on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display is a pixel-art watchface locker with real previews, `.pbw` import, selection and rear-display preview.
- The selected Pebble Time face runs inside native ARM64 QEMU and switches without rebooting the emulator.
- The locker distinguishes a UI selection from the exact UUID acknowledged by PebbleOS: `QUEUED` becomes `ACTIVE / ON AIR` only after runtime confirmation.
- Bundled watchfaces are preinstalled in persistent Basalt SPI flash; imported PBWs are installed once and retained.
- A foreground service keeps PebbleOS alive independently from the main Activity.
- When the app opens in the Titan 2 compact rear window, it routes directly to the fullscreen Pebble framebuffer.
- Rear mode consumes touch and generic motion input, blocks Back/predictive Back and requests full-window system-gesture exclusion.
- Rear-mode detection uses both Android `displayId` and actual window pixel bounds because Titan firmware can expose the rear screen as display 0.

## Current state

Pebblehertz 0.8.5 repairs command ordering and thumbnail attribution exposed by the faster native-TCG runtime, and simplifies Night Mode setup.

Validated in CI:

- native AArch64 TCG Core Devices Pebble QEMU;
- Pebble Time/Basalt-only machine registration;
- official Pebble SDK 4.17 Basalt firmware;
- persistent 16 MB SPI flash and 704 KB micro-flash;
- real `144×168` PebbleOS framebuffer support;
- FIFO-driven framebuffer events with a low-frequency safety fallback;
- automatic PBW installation through BlobDB/AppFetch/PutBytes;
- AppRunState switching confirmed against the exact running UUID;
- paced PutBytes transfers with stale-response protection;
- failed imported-PBW rollback without discarding the running QEMU process;
- cancellation and versioning of asynchronous imported thumbnail captures;
- always-on foreground runtime lifecycle;
- automatic user-configurable Night Mode schedule;
- charging override and below-15% minute-refresh battery saver;
- sixteen real QEMU-rendered bundled previews.

### 0.8.5 command and thumbnail repair

Native TCG can process frames much faster than the old interpreter. A normal seconds tick from the previous face could therefore arrive before PebbleOS completed an AppRunState command. Pebblehertz now queries endpoint `0x0034` and waits for PebbleOS to report the exact requested UUID before publishing `ACTIVE / ON AIR`.

PBW transfer pacing is restored at the protocol boundary, PutBytes acknowledgements are matched to their expected cookie, stale endpoint responses are cleared before each transaction, and an unhealthy protocol socket is recreated without restarting QEMU.

A new selection immediately cancels any pending thumbnail task. Imported preview keys include their stored PBW identity, old capture schemas are invalidated, and the hero card follows the actual active face rather than an unconfirmed UI selection.

### 0.8.5 Night Mode and preview UI

Night Mode no longer has a separate enable checkbox. The user sets `START SLEEP` and `END SLEEP`; the current watchface stays visible while PebbleOS is frozen in the background. Charging always keeps the emulator running.

Main-screen preview mode displays a keyboard hint explaining that the phone Back key exits preview.

### 0.8.4 performance and power

The Android QEMU build uses native AArch64 TCG instead of the TCG interpreter. The production runtime no longer opens a diagnostic serial console or continuously writes QEMU logs.

The rear display waits for FIFO frame events from QEMU instead of polling the framebuffer every 50–250 ms. Pixel conversion runs outside the Android main thread, with a one-second sequence check retained as a recovery fallback.

Below 15% battery, PebbleOS wakes around the minute boundary, refreshes the face and freezes again.

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

Generated bundled previews live in `app/src/main/assets/watchface-thumbnails/` and use the watchface UUID as the filename.

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

## Physical-device validation for 0.8.5

1. Rapidly switch among several bundled faces and confirm only the acknowledged face reaches `ACTIVE / ON AIR`.
2. Import one PBW, switch to bundled faces, import another PBW, and continue switching without restarting PebbleOS.
3. Re-import an updated PBW with the same UUID and confirm it replaces the previous copy.
4. Confirm imported covers belong to the correct face after repeated and rapid selections.
5. Open main-screen preview and exit with the physical keyboard Back key.
6. Verify the styled Night Mode dialog, overnight freeze/resume and charging override.
7. Verify below-15% minute refresh behavior and event-driven framebuffer delivery.

Rollback point: `backup/pebblehertz-0.8.0-before-runtime-fix`.
