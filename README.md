# Pebble Rear Display

Android application for showing Pebble watchfaces on the rear display of the Unihertz Titan 2.

## Product behavior

- The main display contains the watchface library and `.pbw` import.
- When the same launcher activity is opened on a secondary display, it routes to a passive rear-display activity.
- The rear surface consumes touches and shows no controls.
- The selected watchface is persisted between launches.

## Current state

The first scaffold is in place:

- Android app shell without AndroidX or third-party runtime dependencies;
- safe `.pbw` metadata parser for `appinfo.json`;
- seven bundled watchfaces plus manual `.pbw` import;
- persistent selection;
- secondary-display detection;
- fullscreen, non-interactive rear activity;
- temporary clock renderer while PebbleOS/QEMU integration is developed.

The rear display does **not** execute Pebble binaries yet. The next engineering milestone is replacing `RearClockView` with a framebuffer supplied by the Pebble runtime.

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

## Development stack

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- compile/target SDK 36
- minimum SDK 28

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

1. Verify rear-display dimensions, lifecycle and launcher behavior on physical Titan 2 hardware.
2. Add native Pebble runtime module and framebuffer bridge.
3. Boot one Basalt-compatible face without PebbleKit JS.
4. Suspend/resume the runtime with rear-display visibility.
5. Add platform selection and circular Chalk masking.
6. Add PebbleKit JS only after native watchfaces are stable.
