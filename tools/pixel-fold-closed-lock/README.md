# Pixel Fold CLOSED Lock 0.2.0-beta1

Separate standalone Android app, not part of NullWrist/Pebble runtime.

## Why
Original Pixel Fold on Android 17 (felix): requesting rear display 3 through Window Extensions
works, but Android cancels that request on physically folding the phone. The v0.1 app
cannot keep the rear-display session across CLOSED/OPENED transitions.

The beta uses Shizuku shell privileges (no root, no desktop) to request
`cmd device_state state 0` instead, so Android is asked to **hold CLOSED**
rather than enter the temporary rear-camera mode. A base-state override is deliberately
NOT used: Android cancels it on physical posture change (DeviceStateManager documentation). **Not yet tested on real felix**.
No automatic retry or deceptive success message. Only reports command exit status;
the fold/unfold test is still necessary.

## Setup
1. Install [Shizuku](https://shizuku.rikka.app/download/).
2. Enable developer options and Wireless Debugging.
3. Follow Shizuku's on-phone pairing and start steps.
4. Open Pixel Fold CLOSED and permit access to Shizuku.
5. Tap "Зафиксировать CLOSED (0)". Try fold -> unfold -> fold.
6. If Android refuses, tap "Проверить состояние Android" and copy diagnostics.

**Risks / limits:** No root. Shell-level access is a broader privilege than normal APKs.
Shizuku has to be restarted after reboot. The override may also reset after
reboot and on some vendor builds when hardware posture changes. Use reset while
the phone is physically closed. If the command makes the display unusable,
restart the phone to clear the override.

This is not a permanent power-off of the OLED/controller. It is a reversible
framework state override; physically isolating a broken panel cannot be achieved
by a stock Android app.

## Technical
- Uses Shizuku UserService (AIDL) as shell UID.
- Only whitelisted `cmd device_state` operations: state 0, reset, diagnostics.
- UI is offline, no analytics, no internet permission, no forced Accessibility access.
- Does not automate system consent dialogs or require broad Accessibility privileges.
- Side-by-side package `com.manufact.pixelfoldclosed`; v0.1 can be uninstalled separately.
- Uses Shizuku-API 13.1.5 (MIT). See [Shizuku](https://github.com/RikkaApps/Shizuku-API).
