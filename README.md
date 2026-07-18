# Pebble Rear Display

Android application for showing Pebble watchfaces on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display contains the watchface library and `.pbw` import.
- When the app opens in the Titan 2 compact rear window, the launcher activity renders the passive rear surface directly.
- Rear-mode detection uses both Android `displayId` and actual window pixel bounds because Titan firmware can expose the rear screen as display 0.
- The rear surface consumes touches and shows no controls.
- The selected watchface is persisted between launches.
- The preview button opens the same passive renderer on the main display and allows Back to return.

## Current state

The Android application boundary is working on physical Titan 2 hardware:

- safe `.pbw` metadata parser for `appinfo.json`;
- seven bundled watchfaces plus manual `.pbw` import;
- persistent selection;
- Titan 2 compact-window rear detection;
- fullscreen, non-interactive rear surface;
- Android NDK arm64 library and JNI bridge;
- direct `144×168` RGBA framebuffer transfer from C++ to Android;
- native framebuffer animation verified on the physical Titan 2;
- reproducible cross-build pipeline for Core Devices Pebble QEMU;
- on-device QEMU executable probe for version and Pebble machine discovery;
- automatic Pebble Basalt QEMU firmware pinning from the latest SDK.

Version 0.1.1 fixed the first physical-device routing issues. Version 0.1.2 proved that an arm64 native library can generate and update a Pebble-sized framebuffer through JNI on the real device.

The WebView/WebAssembly route was tested and rejected because Titan 2 WebView reported `crossOriginIsolated=false`, preventing the pthread-enabled QEMU build from obtaining `SharedArrayBuffer`. Development therefore continues with a native Android QEMU process and an mmap framebuffer.

The rear display does **not** execute Pebble binaries yet. The active milestone is booting Basalt PebbleOS through the bundled native QEMU and replacing `RearClockView` with the emulator framebuffer.

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

The QEMU cross-build is kept reproducible rather than committing an opaque host binary manually:

```bash
bash scripts/build_pebble_qemu_android.sh
```

The build pins Core Devices QEMU, cross-builds its static GLib/Pixman dependencies for Android arm64, exports Pebble display frames to an mmap file, and produces `libpebble_qemu_exec.so`. A successful CI run pins that executable under `app/src/main/jniLibs/arm64-v8a/` so Android extracts it into the app-native library directory.

The newest installed Pebble SDK firmware can be pinned with:

```bash
python3 scripts/pin_pebble_sdk_firmware.py .
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
gradle :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap

1. Finish the native Android QEMU build and verify `pebble-snowy-bb` on the Titan 2.
2. Boot the pinned Basalt PebbleOS firmware and render its stock framebuffer.
3. Seed one native, offline PBW into the SPI flash and launch it automatically.
4. Replace the temporary rear clock with the selected emulator framebuffer.
5. Suspend and resume QEMU with rear-display visibility.
6. Add Aplite, Chalk and Emery platform images.
7. Add PebbleKit JS only after native watchfaces are stable.
