from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1))


main_path = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
main = Path(main_path).read_text()

if "import android.Manifest;" not in main:
    main = main.replace(
        "package com.manufacttest.pebblereardisplay.ui;\n\n",
        "package com.manufacttest.pebblereardisplay.ui;\n\nimport android.Manifest;\n",
        1,
    )
if "import android.content.pm.PackageManager;" not in main:
    main = main.replace(
        "import android.content.IntentFilter;\n",
        "import android.content.IntentFilter;\nimport android.content.pm.PackageManager;\n",
        1,
    )

replace_once(
    main_path,
    '''    private static final int REQUEST_IMPORT_PBW = 1001;
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_REQUIRED_PERMISSION_REQUESTED =
            "required_permission_requested_v1";
''',
    '''    private static final int REQUEST_IMPORT_PBW = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_REQUIRED_ACCESS_REQUESTED =
            "required_access_requested_v2";
''',
)
main = Path(main_path).read_text()
if "import android.Manifest;" not in main:
    main = main.replace(
        "package com.manufacttest.pebblereardisplay.ui;\n\n",
        "package com.manufacttest.pebblereardisplay.ui;\n\nimport android.Manifest;\n",
        1,
    )
if "import android.content.pm.PackageManager;" not in main:
    main = main.replace(
        "import android.content.IntentFilter;\n",
        "import android.content.IntentFilter;\nimport android.content.pm.PackageManager;\n",
        1,
    )
Path(main_path).write_text(main)

replace_once(
    main_path,
    '''    private View buildReliabilityCard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        panel.setBackground(panelBackground(
                getColor(R.color.surface_warm),
                getColor(R.color.ink),
                dp(1)
        ));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(view -> openBackgroundSettings());

        TextView title = pixelText(
                "ALWAYS-ON STABILITY MODE",
                14,
                getColor(R.color.text_primary)
        );
        panel.addView(title);

        TextView description = bodyText(
                "PebbleOS now runs continuously in one protected mode. Night schedules, "
                        + "charging overrides and low-battery pauses are disabled.",
                12,
                getColor(R.color.text_secondary)
        );
        description.setPadding(0, dp(7), 0, dp(9));
        panel.addView(description);

        systemAccessStatus = pixelText("", 11, getColor(R.color.accent_yellow));
        panel.addView(systemAccessStatus);
        refreshSystemAccessStatus();
        return panel;
    }

    private void refreshSystemAccessStatus() {
        if (systemAccessStatus == null) {
            return;
        }
        boolean unrestricted = isIgnoringBatteryOptimizations();
        systemAccessStatus.setText(unrestricted
                ? "SYSTEM ACCESS: UNRESTRICTED"
                : "SYSTEM ACCESS: ACTION REQUIRED · TAP TO FIX");
        systemAccessStatus.setTextColor(getColor(
                unrestricted ? R.color.accent_mint : R.color.accent_yellow
        ));
    }
''',
    '''    private View buildReliabilityCard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(10), dp(9));
        panel.setMinimumHeight(dp(50));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setElevation(dp(2));
        panel.setOnClickListener(view -> openBackgroundSettings());

        TextView signal = pixelText("●", 13, getColor(R.color.ink));
        signal.setGravity(Gravity.CENTER);
        panel.addView(signal, new LinearLayout.LayoutParams(dp(24), dp(30)));

        systemAccessStatus = pixelText("", 11, getColor(R.color.ink));
        systemAccessStatus.setSingleLine(true);
        systemAccessStatus.setLetterSpacing(0.015f);
        systemAccessStatus.setPadding(dp(4), 0, dp(6), 0);
        panel.addView(systemAccessStatus, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView arrow = pixelText(">", 18, getColor(R.color.ink));
        arrow.setGravity(Gravity.CENTER);
        panel.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(30)));

        refreshSystemAccessStatus();
        return panel;
    }

    private void refreshSystemAccessStatus() {
        if (systemAccessStatus == null) {
            return;
        }
        boolean ready = isIgnoringBatteryOptimizations() && hasNotificationAccess();
        systemAccessStatus.setText(ready
                ? "ALWAYS-ON: ON · ALL GOOD"
                : "ALWAYS-ON: ACTION REQUIRED");
        systemAccessStatus.setTextColor(getColor(R.color.ink));

        View parent = systemAccessStatus.getParent() instanceof View
                ? (View) systemAccessStatus.getParent()
                : null;
        if (parent != null) {
            parent.setBackground(interactivePanelBackground(
                    getColor(ready ? R.color.accent_mint : R.color.accent_yellow),
                    getColor(ready ? R.color.surface_selected : R.color.surface_warm),
                    getColor(R.color.ink)
            ));
        }
    }
''',
)

replace_once(
    main_path,
    '''    private void requestRequiredPermissionsOnFirstLaunch() {
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed() || rearMode || isIgnoringBatteryOptimizations()) {
                refreshSystemAccessStatus();
                return;
            }
            SharedPreferences setup = getSharedPreferences(SETUP_PREFS, MODE_PRIVATE);
            if (setup.getBoolean(KEY_REQUIRED_PERMISSION_REQUESTED, false)) {
                refreshSystemAccessStatus();
                return;
            }
            setup.edit().putBoolean(KEY_REQUIRED_PERMISSION_REQUESTED, true).commit();
            requestBatteryExemption();
        }, 450L);
    }

    private void openBackgroundSettings() {
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption();
            return;
        }
        Toast.makeText(
                this,
                "Battery access is unrestricted. Keep Pebblehertz allowed in DuraSpeed and out of App blocker.",
                Toast.LENGTH_LONG
        ).show();
        openAppDetails();
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager manager = getSystemService(PowerManager.class);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        try {
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(request);
        } catch (RuntimeException error) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (RuntimeException error) {
            showError("Cannot open application settings");
        }
    }
''',
    '''    private void requestRequiredPermissionsOnFirstLaunch() {
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed() || rearMode) {
                return;
            }
            SharedPreferences setup = getSharedPreferences(SETUP_PREFS, MODE_PRIVATE);
            if (setup.getBoolean(KEY_REQUIRED_ACCESS_REQUESTED, false)) {
                refreshSystemAccessStatus();
                return;
            }
            setup.edit().putBoolean(KEY_REQUIRED_ACCESS_REQUESTED, true).commit();
            requestNextRequiredAccess();
        }, 450L);
    }

    private void requestNextRequiredAccess() {
        if (!hasNotificationAccess()) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS
            );
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption();
            return;
        }
        refreshSystemAccessStatus();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
            getWindow().getDecorView().postDelayed(this::requestBatteryExemption, 250L);
        }
        refreshSystemAccessStatus();
    }

    private void openBackgroundSettings() {
        if (!hasNotificationAccess()) {
            openNotificationSettings();
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption();
            return;
        }
        Toast.makeText(
                this,
                "Always-on access is ready. Keep Pebblehertz allowed in DuraSpeed and out of App blocker.",
                Toast.LENGTH_LONG
        ).show();
        openAppDetails();
    }

    private boolean hasNotificationAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager manager = getSystemService(PowerManager.class);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        try {
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(request);
        } catch (RuntimeException error) {
            openAppDetails();
        }
    }

    private void openNotificationSettings() {
        try {
            Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(settings);
        } catch (RuntimeException error) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (RuntimeException error) {
            showError("Cannot open application settings");
        }
    }
''',
)

for forbidden in [
    "KEY_REQUIRED_PERMISSION_REQUESTED",
    "PebbleOS now runs continuously in one protected mode",
    "SYSTEM ACCESS: UNRESTRICTED",
]:
    if forbidden in Path(main_path).read_text():
        raise SystemExit(f"Obsolete MainActivity text remains: {forbidden}")

manifest_path = "app/src/main/AndroidManifest.xml"
manifest = Path(manifest_path).read_text()
manifest = manifest.replace(
    '    <uses-permission android:name="android.permission.INTERNET" />\n',
    '    <uses-permission android:name="android.permission.INTERNET" />\n'
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
    '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n'
    '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n',
    1,
)
receiver = '''        <receiver
            android:name=".runtime.RuntimeBootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>

'''
marker = '        <activity\n            android:name=".ui.RearDisplayActivity"'
if receiver not in manifest:
    if marker not in manifest:
        raise SystemExit("Manifest receiver insertion point missing")
    manifest = manifest.replace(marker, receiver + marker, 1)
Path(manifest_path).write_text(manifest)

Path("app/src/main/java/com/manufacttest/pebblereardisplay/runtime/RuntimeBootReceiver.java").write_text('''package com.manufacttest.pebblereardisplay.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.manufacttest.pebblereardisplay.data.AppPreferences;

/** Restores the selected rear watchface after reboot or an in-place app update. */
public final class RuntimeBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (!new AppPreferences(context).hasSavedWatchfaceSelection()) {
            return;
        }
        try {
            PebbleRuntimeService.start(context);
        } catch (RuntimeException ignored) {
            // Activity startup and the runtime watchdog remain as fallback paths.
        }
    }
}
''')

service_path = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
service = Path(service_path).read_text()
if "import android.os.PowerManager;" not in service:
    service = service.replace(
        "import android.os.Looper;\n",
        "import android.os.Looper;\nimport android.os.PowerManager;\n",
        1,
    )
Path(service_path).write_text(service)
replace_once(
    service_path,
    '''    private void runRuntime(int requestedGeneration, boolean replaceExisting) {
        PebbleQemuProcess current = null;
        try {
''',
    '''    private void runRuntime(int requestedGeneration, boolean replaceExisting) {
        PebbleQemuProcess current = null;
        PowerManager.WakeLock startupWakeLock = acquireStartupWakeLock();
        try {
''',
)
replace_once(
    service_path,
    '''        } finally {
            runtimeBusy = false;
        }
    }

    private void activateSelected(PebbleQemuProcess current, SelectedWatchface selected)
''',
    '''        } finally {
            runtimeBusy = false;
            releaseWakeLock(startupWakeLock);
        }
    }

    private PowerManager.WakeLock acquireStartupWakeLock() {
        PowerManager manager = getSystemService(PowerManager.class);
        if (manager == null) {
            return null;
        }
        PowerManager.WakeLock wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":runtime-start"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(90_000L);
        return wakeLock;
    }

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void activateSelected(PebbleQemuProcess current, SelectedWatchface selected)
''',
)

gradle_path = "app/build.gradle.kts"
gradle = Path(gradle_path).read_text()
if "versionCode = 26" not in gradle:
    raise SystemExit("versionCode 26 not found")
Path(gradle_path).write_text(gradle.replace("versionCode = 26", "versionCode = 27", 1))

changelog_path = "CHANGELOG_0.8.10.md"
changelog = Path(changelog_path).read_text()
changelog = changelog.replace(
    "- Battery-optimization exemption is requested immediately on first launch.\n",
    "- Notification access and battery-optimization exemption are requested in sequence on first launch.\n"
    "- Added boot/update restoration through `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.\n"
    "- Added a bounded 90-second partial wake lock only while QEMU starts or recovers.\n"
    "- Replaced the large stability explanation with a compact, color-coded action card.\n",
)
changelog = changelog.replace(
    "- Version code 26; version name 0.8.10.",
    "- Version code 27; version name 0.8.10.",
)
Path(changelog_path).write_text(changelog)

test_path = "STAGE1_TEST_PLAN.md"
test_plan = Path(test_path).read_text()
test_plan = test_plan.replace(
    "2. Confirm Android immediately requests battery-optimization exemption when needed.\n"
    "3. Return and confirm SYSTEM ACCESS: UNRESTRICTED.\n"
    "4. Confirm there is no runtime-mode chooser, schedule card or notification permission prompt.\n",
    "2. Confirm Android requests notification access on Android 13+.\n"
    "3. Confirm the battery-optimization exemption screen follows when needed.\n"
    "4. Return and confirm the compact card reads `ALWAYS-ON: ON · ALL GOOD`.\n"
    "5. Deny either access and confirm the card reads `ALWAYS-ON: ACTION REQUIRED` and opens the missing setting.\n"
    "6. Confirm there is no runtime-mode chooser or schedule card.\n",
)
test_plan = test_plan.replace(
    "6. Android Task Manager Stop, Force stop and OEM App blocker remain terminal actions.\n",
    "6. Reboot the phone and confirm the selected face is restored after unlock.\n"
    "7. Install an update over the app and confirm the runtime is restored.\n"
    "8. Android Task Manager Stop, Force stop and OEM App blocker remain terminal actions.\n",
)
Path(test_path).write_text(test_plan)

for required in [
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",
    "RuntimeBootReceiver",
]:
    if required not in Path(manifest_path).read_text():
        raise SystemExit(f"Missing manifest item: {required}")

print("v27 access and compact status UI applied")
