# Pebble Rear Display

Android application for showing Pebble watchfaces on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display contains the watchface library, `.pbw` import, selection and rear-display preview.
- When the app opens in the Titan 2 compact rear window, the launcher activity switches directly to the PebbleOS framebuffer.
- Rear-mode detection uses both Android `displayId` and actual window pixel bounds because Titan firmware can expose the rear screen as display 0.
- The rear surface consumes touches, ignores Back and shows no controls or diagnostic labels.
- The selected watchface is persisted between launches.
- The preview button opens the same passive PebbleOS renderer on the main display and allows Back to return.

## Current state

The native PebbleOS runtime is working on physical Titan 2 hardware:

- safe `.pbw` metadata parser for `appinfo.json`;
- seven bundled watchfaces plus manual `.pbw` import;
- persistent selection;
- Titan 2 compact-window rear detection;
- fullscreen, non-interactive rear surface;
- native ARM64 Core Devices Pebble QEMU bundled inside the APK;
- official Pebble SDK 4.17 Basalt firmware pinned with SHA-256 metadata;
- persistent 16 MB SPI flash and 704 KB micro-flash;
- real `144×168` PebbleOS framebuffer rendered directly on Android;
- native QEMU, Pebble machine discovery and real PebbleOS framebuffer verified on the physical Titan 2.

The WebView/WebAssembly route was tested and rejected because Titan 2 WebView reported `crossOriginIsolated=false`, preventing the pthread-enabled QEMU build from obtaining `SharedArrayBuffer`. The application therefore uses a native Android QEMU process and a file-backed framebuffer.

The current PebbleOS image boots successfully and displays the stock “Install an app to continue” screen. The active milestone is installing the selected bundled or imported PBW into the persistent Basalt SPI flash and launching it automatically.

## Bundled watchfaces

The application ships with these pinned PBW packages:

- Big Shadow 2.00.5
- Nyan Cat 8.9
- Pip Boy 100 5.4
- Modern Watchface 3.1.1
- Mario Time 3.41
- 91 Dub 4.0 version 4.21
- YWeather 3.7

Source pages and SHA-256 hashes are recorded in [`bundled-watchfaces.json`](bundled-watchfaces.json) and [`THIRD_PARTY_WATCHFACES.md`](THIRD_PARTY_WATCHFACES.md).

The PBW files live in `app/src/main/assets/watchfaces/` and therefore appear in the app library immediately after installation. The verified fetcher can recreate the asset directory from the recorded public Appstore listings:

```bash
python3 scripts/fetch_bundled_watchfaces.py
```

GitHub Actions runs this command automatically and refuses to build when a downloaded package does not match its pinned SHA-256 hash.

## Native runtime pipeline

The QEMU cross-build is reproducible:

```bash
bash scripts/build_pebble_qemu_android.sh
```

The build pins Core Devices QEMU, cross-builds its static GLib/Pixman dependencies for Android arm64, exports Pebble display frames to a file-backed buffer, and produces `libpebble_qemu_exec.so`. The verified executable is bundled under `app/src/main/jniLibs/arm64-v8a/` and extracted by Android at installation time.

Pebble SDK 4.17 Basalt firmware can be recreated with:

```bash
PEBBLE_SDK_VERSION=4.17 python3 scripts/fetch_pebble_basalt_firmware.py
```

This copies the Basalt micro-flash and a decompressed writable SPI-flash image to `app/src/main/assets/pebble/basalt/` and writes SHA-256 metadata.

## Development stack

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- Android NDK 28
- CMake 3.22.1
- compile/target SDK 36
- minimum SDK 28
- arm64-v8a native runtime target

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

## Roadmap

1. Install the selected native, offline PBW into the persistent Basalt SPI flash.
2. Launch the selected watchface automatically when PebbleOS boots.
3. Reinstall only when the selected PBW changes.
4. Suspend and resume QEMU with rear-display visibility.
5. Add Aplite, Chalk and Emery platform images.
6. Add PebbleKit JS only after native watchfaces are stable.
