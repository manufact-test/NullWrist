# orangre piano 🍊

Minimalist, text-only, orange-black Android HOME launcher designed for the square **1440 x 1440** main display of the Unihertz Titan 2.

An entirely separate Android application from NullWrist. Does not change the rear screen, Pebble runtime, or main NullWrist application.

## Current scope

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

This personal v0.3 build has not yet been device-tested on the Titan 2. Android firmware-specific physical keyboard gestures, system HOME transitions, glass render performance, and soft-keyboard behavior need real device validation. Source package: `com.orangrepiano.launcher`.

Project name is intentionally spelled **orangre piano**.

## v0.2 refinements

- Non-scrolling home screen; removed brand and nonessential labels from HOME.
- Tap time to open the clock; tap date to open the calendar (uses standard Android intents).
- Built-in English and Russian UI setting, including date locale.
- Optional hide top status bar on this launcher; Android does not allow a normal app to disable the notification shade system-wide. System pull-down remains available.
- **v0.1 → v0.2 signing caveat:** the initial APK was built with an ephemeral GitHub debug certificate; v0.2 is signed by a different key. Android will reject an in-place upgrade. Uninstall v0.1 before installing v0.2; this clears its local app preferences and custom wallpaper. NullWrist remains untouched.
- For future upgrades, configure a persistent, private Android signing key through GitHub Actions secrets (never publish the key in this public repository).

## v0.3 UI refinement

- The alphabetical drawer no longer shows brand, title or number of apps; search and A-Z appear higher.
- The previous HOME text navigation is replaced by a square, accessible vector back button on drawer and preferences.
- Home clock/date moved slightly lower. Two square icon buttons beside them: default camera app and real flashlight toggle.
- Flashlight uses Android CameraManager torch mode and runtime CAMERA permission only on first flashlight use; it does not capture images. Camera app shortcut uses an implicit Android intent and does not request camera permission.
- Flashlight on/off styling follows torch status via CameraManager.TorchCallback. If other apps use the camera, torch may become unavailable.
- Debug APK signing currently changes between CI runners: uninstall the old test APK before installing this version, losing launcher-local settings. Fix requires a persistent private signing key.

## v0.3.1 — gesture navigation refinement

- A-Z drawer back arrow moved to the top-right, inline with the search field. Search stays at the top with more room for the list.
- Preferences back arrow moved to the top-right, alongside the PREFERENCES / НАСТРОЙКИ heading.
- Swipe up on home opens the app drawer from below; swipe down **from the top of the drawer list** returns home using a matching vertical transition.
- Closing gesture listens to touch movement without consuming it, to avoid breaking regular vertical list scrolling and letter selection. If the list has been scrolled down, scroll to the beginning before swiping down to close.
- Android back button and the visible square arrow continue to work. The existing reduced-animation option still applies.

## v0.4.0 / ATELIER design system

Premium visual / motion system refresh while retaining working Titan 2 features.

- Custom **5×7 raster clock** drawn on Canvas, not a device font, with deliberate pixel-spacing, copper highlight and subtle minute crossfade. Date remains tappable.
- Unified pearl/copper/obsidian tones, restrained orbital ambient glow and deterministic micro-pixel grid. Photo wallpaper remains usable underneath a translucent legibility scrim.
- Reusable layered glass material: soft inner top glint, light-catching gradient edge, warm translucent interior. Designed as an optical approximation; this is **not** GPU background-blur or refractive glass.
- Tactile micro-motion in favorite entries, app list and icon controls. No heavy lottie/vector images or network assets.
- First-class letter rail: active glass capsule, quick haptic ticks per letter, popup letter lens, animated jump for short distances, direct jump for distant letters. Works with Latin/Cyrillic and regular scroll.
- Settings gain a consistent glass/metal hierarchy, refined toggles, and section hairlines.
- Existing quiet animation setting reduces transitions and haptic effects. The reduced motion option is local to the launcher.
- **Signed debug APK caveat:** ephemeral CI signing keys still require uninstalling the previous build (local settings/wallpaper cleared). A privately stored persistent signing key is necessary for smooth upgrade installs.

## v1.0.0 Release Candidate

- Corrected alphabet glyph optical alignment in each animated capsule by rasterizing exact glyph bounds on Canvas, including Cyrillic/Latin.
- Created bespoke copper/pixel piano Android adaptive icon and monochrome themed variant; available on Android app lists and system surfaces through manifest icon references.
- Upgraded build tooling and target to Android 16 / API 36 for Google Play 2026 minimum target requirements.
- Automated preview lint, APK signature verification, icon/package checks, and production APK/AAB generation **only** with persistent signing secrets; see [RELEASE.md](RELEASE.md).
- No signing secrets committed. An unsigned production release is **not** published as stable; debug-signed RC1 is installable after uninstalling former preview APKs.


## v1.0.1 RC1 — Favorite keys optical polish

- Removed alternating zebra tint on the whole favorite row; all resting rows use the same restrained material.
- Retained the outer tap and long-press hit targets, including empty-slot assignment.
- Split each favorite visually into a fixed-width mono slot index, 7 dp air gap,
  subtly inset glass label capsule, 10 dp end gap, and separate copper pixel marker.
  The capsule uses a delicate border and fades toward the right to avoid large flat stripes.
- Only the inset glass responds to press (soft scaling and copper tint), keeping
  slot numbers and end markers visibly independent.
- Removed full-width dividing lines between favorite rows to preserve clear spacing.
- Preserved 5/6 favorite slots, existing row heights and Titan 2 no-scroll sizing.
- CI builds and lints an Android 16 release candidate; stable store signing remains
  conditional on private production keystore secrets (see RELEASE.md).

## v1.1.0 RC1 — Launcher essentials and first-run configuration

- All favorite-list backgrounds and alternating highlights removed; text-only rows with number, name, and optional *nonzero* notification counter directly beside the app name.
- App counts require explicit Android Notification Listener access; the listener aggregates only notification package names, ignores ongoing/foreground/group-summary notifications, and does not save titles, contents, contacts or individual notification objects.
- A compact live-weather ticker below the date obtains current temperature and conditions from **Open-Meteo** (no account or API key); approximate device location is requested at first launch. No background-location permission, no weather tracking service. Forecasts are cached for 45 minutes and the widget retains the last successful value offline.
- Weather provider credit: [Open-Meteo](https://open-meteo.com/) — weather data CC BY 4.0, free noncommercial access subject to fair-use limits. Commercial distribution requires review of provider terms.
- Two additional matching square vector buttons form a 2×2 quick-action arrangement: phone (system ACTION_DIAL by default) and wallet (choose an installed app on first tap). Both shortcuts can be reconfigured via settings.
- Guided first-run setup requests the HOME role, offers notification-access settings, approximate location consent and optional hiding of the Android status bar. All steps may be skipped and revisited via Settings. Android requires users to approve those settings; this app never circumvents them.
- Setup only uses ACCESS_COARSE_LOCATION, INTERNET and preexisting CAMERA (for torch); no analytics, telemetry or advertising. The sole external weather request goes to api.open-meteo.com.
- Updated Android API 36 debug preview passes GitHub Actions build and lint; stable APK and AAB remain gated on release signing secrets.

## v1.1.1 RC1 — Play Protect sideload compatibility

Google's enhanced fraud protection rejects apps installed from a browser or
file manager when they declare sensitive `BIND_NOTIFICATION_LISTENER_SERVICE`
notification-listener access. This affected the 1.1.0 RC1 direct download.

- Removed the NotificationListenerService component from the Android manifest.
- Removed notification listener onboarding, badge logic, and on-device listener
  implementation. **Notification badges are unavailable in this build.**
- Retained the clean text-only home list, Open-Meteo forecast with optional
  coarse location, 2×2 quick action buttons and first-run launcher setup.
- GitHub Actions checks the **packaged AndroidManifest.xml** to verify the
  sensitive notification permission/service is absent.
- Play Protect stays enabled. Passing the permission-based block is expected,
  but installation still depends on Google's other malware and reputation checks.
- Stable updates remain gated on a persistent, privately held signing key.
