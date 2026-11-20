# Pixel Outer Only — Pixel Fold (2023)
Standalone Android APK for a damaged inner display, developed inside a separate directory of `manufact-test/NullWrist`. The parent Pebble application is untouched.

## Behaviour
- One-way **Enable outer display** via the rear-display device state; inner panel should be off while the override is accepted.
- Original Pixel Fold (`felix`) on Android 14+ is the intended target; **on-device compatibility unverified**.
- Google's system confirmation dialog may appear on every request.
- A foreground service keeps the request process alive; notification opens the app.
- Separate guarded **restore normal mode** button.
- Quick Settings tile only requests outer screen (never toggles toward the broken panel).
- No network permission, no analytics, no root, no debugging permission, no ADB.

## Limits
This is a reversible software display-mode override, *not* a hardware disconnection of the internal panel/sensors. The OS can revoke it on reboot, state transition, process death, or OS upgrade. Android does not let ordinary third-party apps force the CLOSED state permanently. If the rear-display state is absent, the app reports unsupported and offers copyable diagnostics.

## Build
JDK 21, Android SDK 36, Gradle 9.5:
```
gradle :app:assembleDebug
```
The build workflow in `.github/workflows/pixel-fold-outer-only.yml` publishes a test APK to a GitHub prerelease. This beta uses ephemeral debug signing: uninstall before installing a future APK if Android complains that update signatures differ.

## Attribution
Based on [Fold Toggle](https://github.com/reveille45/fold-toggle) (MIT licensed), with adapted package, UI, notification behaviour and single-direction control. Original license: `THIRD_PARTY_LICENSE-FOLD_TOGGLE`.
