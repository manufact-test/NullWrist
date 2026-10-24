# orangre piano 🍊

Minimalist, text-only, orange-black Android HOME launcher designed for the square **1440 x 1440** main display of the Unihertz Titan 2.

An entirely separate Android application from NullWrist. Does not change the rear screen, Pebble runtime, or main NullWrist application.

## v0.2 scope

- Android HOME role (no root), safe to swap back to the stock launcher.
- 5 or 6 selectable and reorderable favorite apps, no app icons.
- Alphabetical app drawer, letter-index jumps, filtering and hardware-keyboard search.
- Local user-selected photo wallpaper, copied into the app's private storage.
- Warm orange/black pixel/glass-inspired composition, quick animations, quiet mode.
- Settings for favorites, 5/6 slots, glass overlay, quiet animation, drawer duplication.
- No network permission, ads, analytics or services.

## Build

```sh
cd orangre-piano
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

The repository workflow `orangre-piano.yml` builds this project independently and publishes artifact `orangre-piano-debug-apk`.

## Install and first launch

1. Install APK on the Titan 2 (same package as v0.1).
2. Launch **orangre piano** from app list.
3. Tap **SET AS DEFAULT HOME** and confirm with Android's standard launcher chooser.
4. In **PREFERENCES**, tap a slot to assign an app.
5. Tap **CHOOSE WALLPAPER** in preferences to select a local photo.

To restore the previous launcher, open **Settings → Apps → Default apps → Home app**.

## Notes

This personal v0.2 build has not yet been device-tested on the Titan 2. Android firmware-specific physical keyboard gestures, system HOME transitions, glass render performance, and soft-keyboard behavior need real device validation. Source package: `com.orangrepiano.launcher`.

Project name is intentionally spelled **orangre piano**.

## v0.2 refinements

- Non-scrolling home screen; removed brand and nonessential labels from HOME.
- Tap time to open the clock; tap date to open the calendar (uses standard Android intents).
- Built-in English and Russian UI setting, including date locale.
- Optional hide top status bar on this launcher; Android does not allow a normal app to disable the notification shade system-wide. System pull-down remains available.
- **v0.1 → v0.2 signing caveat:** the initial APK was built with an ephemeral GitHub debug certificate; v0.2 is signed by a different key. Android will reject an in-place upgrade. Uninstall v0.1 before installing v0.2; this clears its local app preferences and custom wallpaper. NullWrist remains untouched.
- For future upgrades, configure a persistent, private Android signing key through GitHub Actions secrets (never publish the key in this public repository).
