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

Pebblehertz 0.8.1 includes the 0.8.0 pixel-art interface plus a runtime recovery rewrite prompted by physical Titan 2 testing.

Validated in CI:

- native ARM64 Core Devices Pebble QEMU execution assets;
- Pebble Time/Basalt-only machine registration;
- official Pebble SDK 4.17 Basalt firmware;
- persistent 16 MB SPI flash and 704 KB micro-flash;
- real `144×168` PebbleOS framebuffer support;
- automatic PBW installation through BlobDB/AppFetch/PutBytes;
- instant AppRunState switching for installed faces;
- always-on foreground runtime lifecycle;
- adaptive framebuffer polling that copies pixels only after the QEMU frame sequence changes;
- pixel-art Pebblehertz control interface;
- seven real QEMU-rendered bundled previews and on-device thumbnail capture for imported PBWs.

### 0.8.1 runtime repair

PebbleOS readiness is now determined by the actual phone-protocol handshake rather than by searching the diagnostic serial stream for a human-readable boot phrase. The diagnostic UART can contain binary bootloader records on Titan 2, so it is drained and sanitized separately and never controls runtime state.

A failed start now stops and detaches the failed QEMU instance. A later app start or watchface selection creates a clean runtime while retaining the persistent SPI flash. Normal watchface changes still use AppRunState without rebooting QEMU.

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

## Physical-device validation for 0.8.1

1. Switch all seven bundled faces and confirm each reaches `ACTIVE / ON AIR` and appears on the rear display.
2. Close and reopen the main Activity while the foreground runtime stays alive.
3. Restart the runtime and confirm the last selected face returns without a communication-readiness exception.
4. Import one PBW and confirm installation, rear rendering and thumbnail capture.
5. Confirm fullscreen and input locking on the rear display remain unchanged.

Rollback point: `backup/pebblehertz-0.8.0-before-runtime-fix`.
