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

The Android shell is working on physical Titan 2 hardware:

- safe `.pbw` metadata parser for `appinfo.json`;
- seven bundled watchfaces plus manual `.pbw` import;
- persistent selection;
- Titan 2 compact-window rear detection;
- fullscreen, non-interactive rear surface;
- Android NDK/CMake module restricted to `arm64-v8a`;
- JNI bridge with a direct 144×168 RGBA framebuffer;
- native framebuffer probe that generates animated pixels in C++ and renders them in Android.

Version 0.1.1 fixed the first physical-device routing issues. Version 0.1.2 establishes the native rendering boundary that will host QEMU.

The rear display does **not** execute Pebble binaries yet. The next milestone is replacing the native test-pattern generator with the Pebble QEMU machine and its display framebuffer.

## Runtime decision

A QEMU/WebAssembly proof was tested on the Titan 2. Android WebView reported `crossOriginIsolated=false`, so the pthread-enabled QEMU build could not use `SharedArrayBuffer`. The WebView path was abandoned in favor of native QEMU through the Android NDK.

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

## Development stack

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- Android NDK 28.0.13004108
- CMake 3.22.1
- compile/target SDK 36
- minimum SDK 28
- initial native ABI: arm64-v8a

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

1. Verify rear-display dimensions, lifecycle and launcher behavior on physical Titan 2 hardware. ✅
2. Verify native ARM64 library loading and direct framebuffer transfer on the Titan 2.
3. Build the Pebble QEMU machine as an Android shared library.
4. Boot one Basalt-compatible face without PebbleKit JS.
5. Suspend/resume the runtime with rear-display visibility.
6. Add platform selection and circular Chalk masking.
7. Add PebbleKit JS only after native watchfaces are stable.
