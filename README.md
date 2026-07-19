# Pebblehertz

Pebble Time watchfaces running natively on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display is a pixel-art watchface locker with real previews, `.pbw` import, selection and rear-display preview.
- The selected Pebble Time face runs inside native ARM64 QEMU and switches without rebooting the emulator.
- Bundled watchfaces are preinstalled in persistent Basalt SPI flash; imported PBWs are installed once and retained.
- A foreground service keeps PebbleOS alive independently from the main Activity.
- When the app opens in the Titan 2 compact rear window, it routes directly to the fullscreen Pebble framebuffer.
- Rear mode consumes touch and generic motion input, blocks Back/predictive Back and requests full-window system-gesture exclusion.
- Rear-mode detection uses both Android `displayId` and actual window pixel bounds because Titan firmware can expose the rear screen as display 0.

## Current state

Validated on physical Titan 2 hardware:

- native ARM64 Core Devices Pebble QEMU execution;
- Pebble Time/Basalt-only machine registration;
- official Pebble SDK 4.17 Basalt firmware;
- persistent 16 MB SPI flash and 704 KB micro-flash;
- real `144×168` PebbleOS framebuffer rendered directly on Android;
- automatic PBW installation through BlobDB/AppFetch/PutBytes;
- instant AppRunState switching for installed faces;
- always-on foreground runtime lifecycle;
- adaptive framebuffer polling that copies pixels only after the QEMU frame sequence changes;
- pixel-art Pebblehertz control interface;
- seven real QEMU-rendered bundled previews and on-device thumbnail capture for imported PBWs.

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

## Next milestones

1. Physical-device validation and polish of the Pebblehertz 0.8.0 locker.
2. Stable signed release APK and upgrade testing without clearing SPI flash.
3. Optional PebbleKit JS support for configurable and connected watchfaces.
4. Optional Device Owner/kiosk deployment for system-level rear-screen locking.
