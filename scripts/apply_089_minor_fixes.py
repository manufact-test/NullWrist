#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return updated


def patch_build_config() -> None:
    path = "app/build.gradle.kts"
    text = read(path)
    text = replace_once(text, 'versionCode = 22', 'versionCode = 23', 'versionCode')
    text = replace_once(text, 'versionName = "0.8.8"', 'versionName = "0.8.9"', 'versionName')
    write(path, text)


def patch_manifest() -> None:
    path = "app/src/main/AndroidManifest.xml"
    text = read(path)
    for permission in (
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n',
    ):
        text = replace_once(text, permission, "", f"remove {permission.strip()}")

    text = regex_once(
        text,
        r'''        <service\n'''
        r'''            android:name="\.runtime\.PebbleRuntimeService"\n'''
        r'''            android:exported="false"\n'''
        r'''            android:foregroundServiceType="specialUse"\n'''
        r'''            android:stopWithTask="false">\n'''
        r'''            <property\n'''
        r'''                android:name="android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"\n'''
        r'''                android:value="[^"]+" />\n'''
        r'''        </service>''',
        '''        <service\n            android:name=".runtime.PebbleRuntimeService"\n            android:exported="false"\n            android:stopWithTask="false" />''',
        "ordinary runtime service",
    )
    write(path, text)


def patch_repository() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/data/WatchfaceRepository.java"
    text = read(path)
    marker = '''        String uuidPart = parsed.getUuid().replaceAll("[^A-Za-z0-9-]", "");\n'''
    insertion = '''        WatchfaceMetadata duplicate = findDuplicateByUuid(parsed.getUuid());\n        if (duplicate != null) {\n            temporary.delete();\n            throw new DuplicateWatchfaceException(duplicate.getName());\n        }\n\n        String uuidPart = parsed.getUuid().replaceAll("[^A-Za-z0-9-]", "");\n'''
    text = replace_once(text, marker, insertion, "duplicate check")

    marker = '''    private void removeOlderImportsWithUuid(String uuid, File keep) throws IOException {\n'''
    insertion = '''    private WatchfaceMetadata findDuplicateByUuid(String uuid) throws IOException {\n        if (uuid == null || uuid.isBlank()) {\n            return null;\n        }\n        for (WatchfaceMetadata existing : loadAll()) {\n            if (uuid.equalsIgnoreCase(existing.getUuid())) {\n                return existing;\n            }\n        }\n        return null;\n    }\n\n    private void removeOlderImportsWithUuid(String uuid, File keep) throws IOException {\n'''
    text = replace_once(text, marker, insertion, "duplicate lookup")

    marker = '''    private static void copyLimited(InputStream input, FileOutputStream output, long limit)\n'''
    insertion = '''    public static final class DuplicateWatchfaceException extends IOException {\n        public DuplicateWatchfaceException(String existingName) {\n            super("This watchface is already in Pebblehertz: " + existingName);\n        }\n    }\n\n    private static void copyLimited(InputStream input, FileOutputStream output, long limit)\n'''
    text = replace_once(text, marker, insertion, "duplicate exception")
    write(path, text)


def patch_protocol_link() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleProtocolLink.java"
    text = read(path)
    text = replace_once(
        text,
        '''    private static final int QEMU_PROTOCOL_BLUETOOTH = 3;\n''',
        '''    private static final int QEMU_PROTOCOL_BLUETOOTH = 3;\n    private static final int QEMU_PROTOCOL_BATTERY = 5;\n''',
        "battery protocol constant",
    )
    marker = '''    public byte[] awaitEndpoint(\n'''
    insertion = '''    public void setBatteryState(int percentage, boolean chargerConnected) throws IOException {\n        int safePercentage = Math.max(0, Math.min(100, percentage));\n        sendQemuPacket(\n                QEMU_PROTOCOL_BATTERY,\n                new byte[]{\n                        (byte) safePercentage,\n                        (byte) (chargerConnected ? 1 : 0)\n                }\n        );\n    }\n\n    public byte[] awaitEndpoint(\n'''
    text = replace_once(text, marker, insertion, "battery protocol sender")
    write(path, text)


def patch_qemu_process() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleQemuProcess.java"
    text = read(path)
    marker = '''    public synchronized boolean isRunning() {\n'''
    insertion = '''    public synchronized void updateBatteryState(\n            int percentage,\n            boolean chargerConnected\n    ) throws IOException {\n        PebbleProtocolLink current = protocolLink;\n        if (!isRunning() || current == null || !current.isHealthy()) {\n            return;\n        }\n        current.setBatteryState(percentage, chargerConnected);\n    }\n\n    public synchronized boolean isRunning() {\n'''
    text = replace_once(text, marker, insertion, "runtime battery bridge")
    write(path, text)


def patch_runtime_service() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
    text = read(path)
    text = replace_once(
        text,
        '''import android.os.Build;\n''',
        '''import android.os.BatteryManager;\nimport android.os.Build;\n''',
        "BatteryManager import",
    )
    text = replace_once(
        text,
        '''    private volatile RuntimePowerPolicy.Mode powerMode = RuntimePowerPolicy.Mode.RUNNING;\n''',
        '''    private volatile RuntimePowerPolicy.Mode powerMode = RuntimePowerPolicy.Mode.RUNNING;\n    private volatile int phoneBatteryPercentage = 100;\n    private volatile boolean phoneChargerConnected;\n''',
        "battery fields",
    )
    text = replace_once(
        text,
        '''        public void onReceive(Context context, Intent intent) {\n            policyHandler.removeCallbacks(policyTick);\n            policyHandler.post(policyTick);\n''',
        '''        public void onReceive(Context context, Intent intent) {\n            updateBatterySnapshot(intent);\n            executor.execute(PebbleRuntimeService.this::syncBatteryToRuntime);\n            policyHandler.removeCallbacks(policyTick);\n            policyHandler.post(policyTick);\n''',
        "battery receiver",
    )
    text = regex_once(
        text,
        r'''    private static void send\(Context context, String action\) \{\n'''
        r'''        Intent intent = new Intent\(context, PebbleRuntimeService\.class\)\.setAction\(action\);\n'''
        r'''        if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.O\) \{\n'''
        r'''            context\.startForegroundService\(intent\);\n'''
        r'''        \} else \{\n'''
        r'''            context\.startService\(intent\);\n'''
        r'''        \}\n'''
        r'''    \}''',
        '''    private static void send(Context context, String action) {\n        Intent intent = new Intent(context, PebbleRuntimeService.class).setAction(action);\n        context.startService(intent);\n    }''',
        "ordinary service start",
    )
    text = replace_once(
        text,
        '''        createNotificationChannel();\n        promoteToForeground(buildNotification("Pebble Time is starting"));\n''',
        "",
        "remove foreground promotion",
    )
    text = replace_once(
        text,
        '''            stopForeground(STOP_FOREGROUND_REMOVE);\n''',
        "",
        "remove stopForeground",
    )
    text = replace_once(
        text,
        '''            current.waitForFirmwareReady(40_000);\n            ensureCurrent(requestedGeneration);\n\n            setStatus("Waiting for Pebble display…");\n''',
        '''            current.waitForFirmwareReady(40_000);\n            ensureCurrent(requestedGeneration);\n            current.updateBatteryState(phoneBatteryPercentage, phoneChargerConnected);\n\n            setStatus("Waiting for Pebble display…");\n''',
        "initial battery sync",
    )
    marker = '''    private void applyPowerPolicy() {\n'''
    insertion = '''    private void updateBatterySnapshot(Intent intent) {\n        if (intent == null) {\n            return;\n        }\n        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);\n        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);\n        if (level >= 0 && scale > 0) {\n            phoneBatteryPercentage = Math.max(\n                    0,\n                    Math.min(100, Math.round(level * 100f / scale))\n            );\n        }\n        int status = intent.getIntExtra(\n                BatteryManager.EXTRA_STATUS,\n                BatteryManager.BATTERY_STATUS_UNKNOWN\n        );\n        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);\n        phoneChargerConnected = plugged != 0\n                || status == BatteryManager.BATTERY_STATUS_CHARGING\n                || status == BatteryManager.BATTERY_STATUS_FULL;\n    }\n\n    private void syncBatteryToRuntime() {\n        PebbleQemuProcess current = runtime;\n        if (current == null || !current.isRunning()) {\n            return;\n        }\n        try {\n            current.updateBatteryState(phoneBatteryPercentage, phoneChargerConnected);\n        } catch (IOException ignored) {\n            // The next sticky battery broadcast or runtime start will retry the sync.\n        }\n    }\n\n    private void applyPowerPolicy() {\n'''
    text = replace_once(text, marker, insertion, "battery sync helpers")
    text = regex_once(
        text,
        r'''    private void updateNotification\(String text\) \{\n'''
        r'''        NotificationManager manager = getSystemService\(NotificationManager\.class\);\n'''
        r'''        manager\.notify\(NOTIFICATION_ID, buildNotification\(text\)\);\n'''
        r'''    \}''',
        '''    private void updateNotification(String text) {\n        // Runtime state is shown inside Pebblehertz; Android shade notifications are disabled.\n    }''',
        "disable state notifications",
    )
    write(path, text)


def patch_main_activity() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
    text = read(path)
    text = replace_once(
        text,
        '''import android.app.Dialog;\n''',
        '''import android.app.Dialog;\nimport android.app.TimePickerDialog;\n''',
        "TimePickerDialog import",
    )
    text = replace_once(
        text,
        '''        TextView footer = pixelText("PEBBLE TIME / BASALT 144x168", 11, getColor(R.color.text_muted));\n''',
        '''        root.addView(buildSupportButton(), matchWidthWrapHeight(dp(14)));\n\n        TextView footer = pixelText("PEBBLE TIME / BASALT 144x168", 11, getColor(R.color.text_muted));\n''',
        "support button placement",
    )
    marker = '''    private View buildHeader() {\n'''
    insertion = '''    private View buildSupportButton() {\n        TextView support = pixelButton(\n                "SUPPORT PEBBLEHERTZ",\n                getColor(R.color.accent_mint),\n                getColor(R.color.ink)\n        );\n        support.setOnClickListener(view -> showSupportDialog());\n        support.setLayoutParams(new LinearLayout.LayoutParams(\n                LinearLayout.LayoutParams.MATCH_PARENT,\n                dp(48)\n        ));\n        return support;\n    }\n\n    private void showSupportDialog() {\n        String[] platforms = {"Wise", "PayPal"};\n        new AlertDialog.Builder(this)\n                .setTitle("Support Pebblehertz")\n                .setMessage("Choose a platform. Thank you for helping the project grow!")\n                .setItems(platforms, (dialog, which) -> openExternalLink(\n                        which == 0\n                                ? "https://wise.com/pay/me/ilyas709"\n                                : "https://www.paypal.me/myarrogantfox"\n                ))\n                .setNegativeButton("Cancel", null)\n                .show();\n    }\n\n    private void openExternalLink(String url) {\n        try {\n            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));\n        } catch (RuntimeException exception) {\n            showError("Cannot open the donation page");\n        }\n    }\n\n    private View buildHeader() {\n'''
    text = replace_once(text, marker, insertion, "support dialog")

    text = replace_once(
        text,
        '''    EditText start = timeField(AppPreferences.formatMinutes(\n''',
        '''    TextView start = timeField(AppPreferences.formatMinutes(\n''',
        "start time field type",
    )
    text = replace_once(
        text,
        '''    EditText end = timeField(AppPreferences.formatMinutes(\n''',
        '''    TextView end = timeField(AppPreferences.formatMinutes(\n''',
        "end time field type",
    )
    text = regex_once(
        text,
        r'''private EditText timeField\(String value\) \{.*?\n\}\n\nprivate static int parseTime''',
        '''private TextView timeField(String value) {\n    TextView field = pixelText(value, 18, getColor(R.color.text_primary));\n    field.setGravity(Gravity.CENTER);\n    field.setPadding(dp(10), 0, dp(10), 0);\n    field.setBackground(panelBackground(\n            getColor(R.color.paper),\n            getColor(R.color.ink),\n            dp(1)\n    ));\n    field.setClickable(true);\n    field.setFocusable(true);\n    field.setOnClickListener(view -> {\n        int current = parseTime(field.getText().toString());\n        if (current < 0) {\n            current = 0;\n        }\n        TimePickerDialog picker = new TimePickerDialog(\n                this,\n                (timePicker, hour, minute) -> field.setText(String.format(\n                        Locale.US,\n                        "%02d:%02d",\n                        hour,\n                        minute\n                )),\n                current / 60,\n                current % 60,\n                true\n        );\n        picker.show();\n    });\n    return field;\n}\n\nprivate static int parseTime''',
        "read-only time picker field",
        flags=re.S,
    )
    text = regex_once(
        text,
        r'''    private void maybeRequestBackgroundSetup\(\) \{.*?\n    private void maybeShowBatteryPrompt\(\) \{''',
        '''    private void maybeRequestBackgroundSetup() {\n        getWindow().getDecorView().post(this::maybeShowBatteryPrompt);\n    }\n\n    private void maybeShowBatteryPrompt() {''',
        "remove notification permission prompt",
        flags=re.S,
    )
    write(path, text)


def main() -> None:
    patch_build_config()
    patch_manifest()
    patch_repository()
    patch_protocol_link()
    patch_qemu_process()
    patch_runtime_service()
    patch_main_activity()
    print("Pebblehertz 0.8.9 minor fixes applied")


if __name__ == "__main__":
    main()
