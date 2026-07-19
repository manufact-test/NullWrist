#!/usr/bin/env python3
from pathlib import Path
from textwrap import dedent

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"marker not found in {path}: {old[:100]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched {path}")


main = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
replace(
    main,
    "import android.widget.LinearLayout;\nimport android.widget.ScrollView;\nimport android.widget.TextView;",
    "import android.text.InputType;\nimport android.widget.CheckBox;\nimport android.widget.EditText;\nimport android.widget.LinearLayout;\nimport android.widget.ScrollView;\nimport android.widget.TextView;",
)
replace(
    main,
    "    private TextView runtimeStatusLabel;\n    private TextView runtimeLed;",
    "    private TextView runtimeStatusLabel;\n    private TextView runtimeLed;\n    private TextView powerScheduleSummary;",
)
replace(
    main,
    '''    private final BroadcastReceiver thumbnailReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!rearMode) {
                renderCatalog();
            }
        }
    };''',
    '''    private final BroadcastReceiver thumbnailReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PebbleRuntimeService.ACTION_SELECTION_FAILED.equals(intent.getAction())) {
                String message = intent.getStringExtra(
                        PebbleRuntimeService.EXTRA_SELECTION_FAILURE
                );
                if (message != null && !message.isBlank()) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
                reloadCatalog();
                return;
            }
            if (!rearMode) {
                renderCatalog();
            }
        }
    };''',
)
replace(
    main,
    "        root.addView(buildActionRow(), matchWidthWrapHeight(dp(10)));\n        root.addView(buildReliabilityCard(), matchWidthWrapHeight(dp(20)));",
    "        root.addView(buildActionRow(), matchWidthWrapHeight(dp(10)));\n        root.addView(buildPowerScheduleCard(), matchWidthWrapHeight(dp(10)));\n        root.addView(buildReliabilityCard(), matchWidthWrapHeight(dp(20)));",
)
replace(
    main,
    "        IntentFilter filter = new IntentFilter(WatchfaceThumbnailRepository.ACTION_THUMBNAIL_UPDATED);",
    "        IntentFilter filter = new IntentFilter(WatchfaceThumbnailRepository.ACTION_THUMBNAIL_UPDATED);\n        filter.addAction(PebbleRuntimeService.ACTION_SELECTION_FAILED);",
)
replace(
    main,
    '''        if (status != null && !status.isBlank()) {
            runtimeLed.setTextColor(getColor(R.color.accent_yellow));
            runtimeStatusLabel.setText(shortStatus(status));
        } else {
            runtimeLed.setTextColor(getColor(R.color.accent_mint));
            runtimeStatusLabel.setText("RUNTIME ONLINE");
        }''',
    '''        if (status != null && !status.isBlank()) {
            runtimeLed.setTextColor(getColor(R.color.accent_yellow));
            runtimeStatusLabel.setText(shortStatus(status));
        } else {
            String powerMode = PebbleRuntimeService.getPowerModeLabel();
            if (powerMode != null) {
                runtimeLed.setTextColor(getColor(R.color.accent_yellow));
                runtimeStatusLabel.setText(powerMode);
            } else {
                runtimeLed.setTextColor(getColor(R.color.accent_mint));
                runtimeStatusLabel.setText("RUNTIME ONLINE");
            }
        }''',
)

path = ROOT / main
text = path.read_text(encoding="utf-8")
marker = "    private View buildReliabilityCard() {\n"
if marker not in text:
    raise SystemExit("buildReliabilityCard marker missing")
methods = dedent(r'''
    private View buildPowerScheduleCard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(13), dp(12), dp(13), dp(12));
        panel.setBackground(panelBackground(
                getColor(R.color.surface),
                getColor(R.color.ink),
                dp(1)
        ));

        TextView title = pixelText("POWER SCHEDULE // 24H", 13, getColor(R.color.text_primary));
        panel.addView(title);

        powerScheduleSummary = bodyText("", 12, getColor(R.color.text_secondary));
        powerScheduleSummary.setPadding(0, dp(6), 0, dp(10));
        panel.addView(powerScheduleSummary);

        TextView edit = pixelButton(
                "EDIT SLEEP SCHEDULE",
                getColor(R.color.paper),
                getColor(R.color.ink)
        );
        edit.setOnClickListener(view -> showPowerScheduleDialog());
        panel.addView(edit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));
        refreshPowerScheduleSummary();
        return panel;
    }

    private void refreshPowerScheduleSummary() {
        if (powerScheduleSummary == null || preferences == null) {
            return;
        }
        if (preferences.isSleepScheduleEnabled()) {
            powerScheduleSummary.setText(
                    "FREEZE "
                            + AppPreferences.formatMinutes(preferences.getSleepStartMinutes())
                            + "–"
                            + AppPreferences.formatMinutes(preferences.getSleepEndMinutes())
                            + "  /  CHARGING OVERRIDES  /  ≤15% MINUTE SAVER"
            );
        } else {
            powerScheduleSummary.setText(
                    "SCHEDULE OFF  /  CHARGING ALWAYS ON  /  ≤15% MINUTE SAVER"
            );
        }
    }

    private void showPowerScheduleDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), 0);

        CheckBox enabled = new CheckBox(this);
        enabled.setText("Freeze PebbleOS on schedule");
        enabled.setChecked(preferences.isSleepScheduleEnabled());
        form.addView(enabled);

        TextView startLabel = bodyText("Freeze from (HH:mm)", 13, getColor(R.color.text_secondary));
        startLabel.setPadding(0, dp(10), 0, dp(4));
        form.addView(startLabel);
        EditText start = timeField(AppPreferences.formatMinutes(
                preferences.getSleepStartMinutes()
        ));
        form.addView(start);

        TextView endLabel = bodyText("Resume at (HH:mm)", 13, getColor(R.color.text_secondary));
        endLabel.setPadding(0, dp(10), 0, dp(4));
        form.addView(endLabel);
        EditText end = timeField(AppPreferences.formatMinutes(
                preferences.getSleepEndMinutes()
        ));
        form.addView(end);

        TextView note = bodyText(
                "24-hour format. Charging always keeps PebbleOS running. "
                        + "Below 15% battery it wakes once per minute to refresh the face.",
                12,
                getColor(R.color.text_muted)
        );
        note.setPadding(0, dp(12), 0, 0);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PebbleOS sleep schedule")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int startMinutes = parseTime(start.getText().toString());
                    int endMinutes = parseTime(end.getText().toString());
                    if (startMinutes < 0 || endMinutes < 0) {
                        Toast.makeText(
                                this,
                                "Use 24-hour HH:mm format, for example 23:30",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    preferences.setSleepSchedule(
                            enabled.isChecked(),
                            startMinutes,
                            endMinutes
                    );
                    refreshPowerScheduleSummary();
                    PebbleRuntimeService.refreshPowerPolicy(this);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private EditText timeField(String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setInputType(
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME
        );
        return field;
    }

    private static int parseTime(String value) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}")) {
            return -1;
        }
        String[] parts = value.split(":", 2);
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

''')
path.write_text(text.replace(marker, methods + marker, 1), encoding="utf-8")
print("inserted power schedule UI")

replace(
    main,
    '        updateRuntimeStatus("Launching " + watchface.getName(), null);\n        PebbleRuntimeService.select(this);',
    '        PebbleRuntimeService.select(this);',
)
replace(
    main,
    '                updateRuntimeStatus("Launching " + replacement.getName(), null);\n                PebbleRuntimeService.select(this);',
    '                PebbleRuntimeService.select(this);',
)

replace(
    "scripts/build_pebble_qemu_android.sh",
    "    --enable-tcg-interpreter \\\n",
    "    --enable-tcg \\\n",
)
replace(
    "app/build.gradle.kts",
    '        versionCode = 17\n        versionName = "0.8.3"',
    '        versionCode = 18\n        versionName = "0.8.4"',
)

installer_path = ROOT / "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleAppInstaller.java"
installer = installer_path.read_text(encoding="utf-8")
old = "            publishProgress();\n            Thread.sleep(4);"
if old not in installer:
    raise SystemExit("installer sleep marker missing")
installer = installer.replace(old, "            publishProgress();", 1)
installer = installer.replace(
    "import java.util.concurrent.atomic.AtomicInteger;",
    "import java.util.concurrent.TimeUnit;\nimport java.util.concurrent.atomic.AtomicInteger;",
    1,
)
installer = installer.replace(
    "    private int totalSize;\n",
    "    private int totalSize;\n    private int lastPublishedPercent = -1;\n    private long lastPublishedNanos;\n",
    1,
)
installer = installer.replace(
    '''        int percent = totalSize <= 0 ? 0 : Math.min(100, Math.round(totalSent * 100f / totalSize));
        progressListener.onProgress("Installing watchface… " + percent + "%", totalSent, totalSize);''',
    '''        int percent = totalSize <= 0 ? 0 : Math.min(100, Math.round(totalSent * 100f / totalSize));
        long now = System.nanoTime();
        if (percent < 100
                && percent - lastPublishedPercent < 2
                && now - lastPublishedNanos < TimeUnit.MILLISECONDS.toNanos(200)) {
            return;
        }
        lastPublishedPercent = percent;
        lastPublishedNanos = now;
        progressListener.onProgress("Installing watchface… " + percent + "%", totalSent, totalSize);''',
    1,
)
installer_path.write_text(installer, encoding="utf-8")
print("simplified PBW progress")

for relative in (
    "scripts/apply_v084_ui.py",
    ".github/workflows/apply-v084-ui.yml",
):
    target = ROOT / relative
    if target.exists():
        target.unlink()
        print(f"removed {relative}")
