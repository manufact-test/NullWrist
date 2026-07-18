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
- bundled and imported watchface catalog;
- persistent selection;
- secondary-display detection;
- fullscreen, non-interactive rear activity;
- temporary clock renderer while PebbleOS/QEMU integration is developed.

The rear display does **not** execute Pebble binaries yet. The next engineering milestone is replacing `RearClockView` with a framebuffer supplied by the Pebble runtime.

## Development stack

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- compile/target SDK 36
- minimum SDK 28

Open the project in Android Studio or run with an installed Gradle 9.5.0:

```bash
gradle :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Test watchfaces

The app automatically bundles any `.pbw` packages placed in `app/src/debug/assets/watchfaces/` into debug builds. The seven supplied test packages have been inspected and documented, but their binary files are not part of the first source commit.

Keep test packages in the debug source set and exclude them from release builds until redistribution rights for each package and its embedded artwork/fonts are documented. See [`THIRD_PARTY_WATCHFACES.md`](THIRD_PARTY_WATCHFACES.md).

## Roadmap

1. Verify rear-display dimensions, lifecycle and launcher behavior on physical Titan 2 hardware.
2. Add native Pebble runtime module and framebuffer bridge.
3. Boot one Basalt-compatible face without PebbleKit JS.
4. Suspend/resume the runtime with rear-display visibility.
5. Add platform selection and circular Chalk masking.
6. Add PebbleKit JS only after native watchfaces are stable.
