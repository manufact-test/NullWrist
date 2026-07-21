from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected one match in {path}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))


def sub_once(path: str, pattern: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text()
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one regex match in {path}: {pattern[:80]!r}")
    file.write_text(updated)


service = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
replace_once(service, "import android.app.Notification;\n", "import android.app.AlarmManager;\nimport android.app.Notification;\n")
replace_once(service, "import android.os.Looper;\n", "import android.os.Looper;\nimport android.os.SystemClock;\n")
replace_once(
    service,
    '    private static final String ACTION_APPLY_RUNTIME_MODE =\n            "com.manufacttest.pebblereardisplay.action.APPLY_RUNTIME_MODE";\n',
    '    private static final String ACTION_APPLY_RUNTIME_MODE =\n            "com.manufacttest.pebblereardisplay.action.APPLY_RUNTIME_MODE";\n'
    '    private static final String ACTION_RECOVER_TASK =\n'
    '            "com.manufacttest.pebblereardisplay.action.RECOVER_AFTER_TASK_REMOVAL";\n'
)
replace_once(
    service,
    '    private static final String NOTIFICATION_CHANNEL_ID = "pebblehertz_runtime";\n'
    '    private static final int NOTIFICATION_ID = 168;\n',
    '    private static final String NOTIFICATION_CHANNEL_ID = "pebblehertz_runtime";\n'
    '    private static final int NOTIFICATION_ID = 168;\n'
    '    private static final int RECOVERY_REQUEST_CODE = 810;\n'
    '    private static final long RECOVERY_DELAY_MILLIS = 1_200L;\n'
)
replace_once(
    service,
    '    private volatile boolean foreground;\n'
    '    private volatile String notificationText = "Starting PebbleOS…";\n',
    '    private volatile boolean foreground;\n'
    '    private volatile String notificationText = "Rear watchface active";\n'
    '    private volatile boolean selectionQueuedForWake;\n'
    '    private volatile boolean schedulePaused;\n'
)
replace_once(
    service,
    '        return null;\n    }\n\n    public static void addListener(Listener listener) {\n',
    '        return null;\n    }\n\n'
    '    public static boolean isSelectionQueuedForWake() {\n'
    '        PebbleRuntimeService current = instance;\n'
    '        return current != null && current.selectionQueuedForWake;\n'
    '    }\n\n'
    '    public static void addListener(Listener listener) {\n'
)
sub_once(
    service,
    r'    @Override\n    public int onStartCommand\(Intent intent, int flags, int startId\) \{.*?\n    \}\n\n    @Override\n    public IBinder onBind',
    '''    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cancelTaskRemovalRecovery();
            generation.incrementAndGet();
            stopRuntime();
            demoteFromForeground();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_APPLY_RUNTIME_MODE.equals(action)) {
            applyRuntimeModeInternal(notificationText);
            notifyListeners();
            return START_STICKY;
        }
        if (ACTION_RECOVER_TASK.equals(action)) {
            applyRuntimeModeInternal("Rear watchface active");
            scheduleRuntime(false);
            return START_STICKY;
        }
        if (ACTION_SELECT.equals(action)) {
            scheduleSelection();
        } else if (ACTION_REFRESH_POWER.equals(action)) {
            applyPowerPolicy();
        } else {
            scheduleRuntime(ACTION_RESTART.equals(action));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind'''
)
replace_once(
    service,
    '    public IBinder onBind(Intent intent) {\n        return null;\n    }\n\n    @Override\n    public void onDestroy() {\n',
    '    public IBinder onBind(Intent intent) {\n        return null;\n    }\n\n'
    '    @Override\n'
    '    public void onTaskRemoved(Intent rootIntent) {\n'
    '        scheduleTaskRemovalRecovery();\n'
    '        super.onTaskRemoved(rootIntent);\n'
    '    }\n\n'
    '    @Override\n'
    '    public void onDestroy() {\n'
)
replace_once(
    service,
    '        if (requestedGeneration != generation.get()) {\n            return;\n        }\n\n        PebbleQemuProcess current = runtime;\n',
    '''        if (requestedGeneration != generation.get()) {
            return;
        }

        RuntimePowerPolicy.Snapshot snapshot = RuntimePowerPolicy.evaluate(
                this,
                new AppPreferences(this)
        );
        powerMode = snapshot.mode;
        if (snapshot.batteryPercent >= 0) {
            phoneBatteryPercentage = snapshot.batteryPercent;
        }
        phoneChargerConnected = snapshot.charging;
        if (RuntimeSelectionPolicy.shouldQueue(snapshot.mode, snapshot.charging)) {
            selectionQueuedForWake = true;
            thumbnailGeneration.incrementAndGet();
            notifyListeners();
            return;
        }

        PebbleQemuProcess current = runtime;
'''
)
replace_once(
    service,
    '        activeStorageId = selected.metadata.getStorageId();\n        starting = false;\n        status = null;\n',
    '        activeStorageId = selected.metadata.getStorageId();\n'
    '        selectionQueuedForWake = false;\n'
    '        schedulePaused = false;\n'
    '        starting = false;\n'
    '        status = null;\n'
)
sub_once(
    service,
    r'    private void applyPowerPolicy\(\) \{.*?\n    \}\n\n    private void scheduleNextLowBatteryPulse',
    '''    private void applyPowerPolicy() {
        RuntimePowerPolicy.Snapshot snapshot = RuntimePowerPolicy.evaluate(
                this,
                new AppPreferences(this)
        );
        RuntimePowerPolicy.Mode previousMode = powerMode;
        powerMode = snapshot.mode;
        if (snapshot.batteryPercent >= 0) {
            phoneBatteryPercentage = snapshot.batteryPercent;
        }
        phoneChargerConnected = snapshot.charging;
        policyHandler.removeCallbacks(lowBatteryPause);
        policyHandler.removeCallbacks(lowBatteryPulse);

        PebbleQemuProcess current = runtime;
        if (runtimeBusy || current == null || !current.isRunning()) {
            notifyListeners();
            return;
        }

        boolean applyQueuedSelection = RuntimeSelectionPolicy.shouldApplyQueuedSelection(
                previousMode,
                snapshot.mode,
                selectionQueuedForWake
        );
        try {
            if (snapshot.mode == RuntimePowerPolicy.Mode.SCHEDULED_FREEZE) {
                if (!schedulePaused) {
                    thumbnailGeneration.incrementAndGet();
                    current.pause();
                    schedulePaused = true;
                }
            } else {
                if (schedulePaused) {
                    current.resume();
                    schedulePaused = false;
                }
                if (snapshot.mode == RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE) {
                    current.resume();
                    policyHandler.postDelayed(lowBatteryPause, LOW_BATTERY_AWAKE_MILLIS);
                    scheduleNextLowBatteryPulse();
                }
                if (previousMode != snapshot.mode) {
                    updateNotification("Rear watchface active");
                }
            }
            status = null;
            failure = null;
            notifyListeners();
        } catch (IOException error) {
            reportFailure(error);
            return;
        }

        if (applyQueuedSelection) {
            scheduleSelection();
        }
    }

    private void scheduleNextLowBatteryPulse'''
)
replace_once(
    service,
    '                runtime = null;\n                activeStorageId = null;\n',
    '                runtime = null;\n'
    '                activeStorageId = null;\n'
    '                selectionQueuedForWake = false;\n'
    '                schedulePaused = false;\n'
)
replace_once(
    service,
    '        runtime = null;\n        activeStorageId = null;\n        starting = false;\n',
    '        runtime = null;\n'
    '        activeStorageId = null;\n'
    '        selectionQueuedForWake = false;\n'
    '        schedulePaused = false;\n'
    '        starting = false;\n'
)
sub_once(
    service,
    r'    private void updateNotification\(String text\) \{.*?\n    \}\n\n    private static String safeMessage',
    '''    private void scheduleTaskRemovalRecovery() {
        AppPreferences preferences = new AppPreferences(this);
        if (!RuntimeRecoveryPolicy.shouldRecover(
                preferences.getRuntimeMode(),
                preferences.hasSavedWatchfaceSelection()
        )) {
            return;
        }

        Intent recovery = new Intent(this, PebbleRuntimeService.class)
                .setAction(ACTION_RECOVER_TASK);
        PendingIntent pending = PendingIntent.getForegroundService(
                this,
                RECOVERY_REQUEST_CODE,
                recovery,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = getSystemService(AlarmManager.class);
        if (alarm != null) {
            alarm.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RECOVERY_DELAY_MILLIS,
                    pending
            );
        }
        try {
            startForegroundService(recovery);
        } catch (RuntimeException ignored) {
            // AlarmManager remains as the fallback if immediate restart is restricted.
        }
    }

    private void cancelTaskRemovalRecovery() {
        PendingIntent pending = PendingIntent.getForegroundService(
                this,
                RECOVERY_REQUEST_CODE,
                new Intent(this, PebbleRuntimeService.class).setAction(ACTION_RECOVER_TASK),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pending == null) {
            return;
        }
        AlarmManager alarm = getSystemService(AlarmManager.class);
        if (alarm != null) {
            alarm.cancel(pending);
        }
        pending.cancel();
    }

    private void updateNotification(String ignoredText) {
        notificationText = "Rear watchface active";
        if (new AppPreferences(this).isReliableRuntime() && !foreground) {
            promoteToForeground(notificationText);
        }
    }

    private void applyRuntimeModeInternal(String text) {
        if (new AppPreferences(this).isReliableRuntime()) {
            promoteToForeground(text);
        } else {
            demoteFromForeground();
        }
    }

    private void promoteToForeground(String ignoredText) {
        notificationText = "Rear watchface active";
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foreground = true;
    }

    private void demoteFromForeground() {
        if (!foreground) {
            return;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Pebblehertz",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the rear watchface active.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Pebblehertz")
                .setContentText("Rear watchface active")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED);
        }
        return builder.build();
    }

    private static String safeMessage'''
)

activity = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
file = Path(activity)
text = file.read_text()
text = text.replace("import android.Manifest;\n", "")
text = text.replace("import android.content.pm.PackageManager;\n", "")
text = text.replace("    private static final int REQUEST_NOTIFICATIONS = 1002;\n", "")
text = text.replace(
    "    private String renderedActiveStorageId;\n",
    "    private String renderedActiveStorageId;\n    private boolean renderedSelectionQueuedForWake;\n",
    1
)
text = text.replace(
    '''        String activeId = PebbleRuntimeService.getActiveStorageId();
        if (!sameStorageId(renderedActiveStorageId, activeId)) {
            renderCatalog();
        }
''',
    '''        String activeId = PebbleRuntimeService.getActiveStorageId();
        boolean queuedForWake = PebbleRuntimeService.isSelectionQueuedForWake();
        if (!sameStorageId(renderedActiveStorageId, activeId)
                || renderedSelectionQueuedForWake != queuedForWake) {
            renderCatalog();
        }
''',
    1
)
text = text.replace(
    "        renderedActiveStorageId = activeId;\n        WatchfaceMetadata selected = null;\n",
    "        renderedActiveStorageId = activeId;\n"
    "        renderedSelectionQueuedForWake = PebbleRuntimeService.isSelectionQueuedForWake();\n"
    "        WatchfaceMetadata selected = null;\n",
    1
)
text = text.replace(
    '''        TextView action = pixelText(
                active ? "ON AIR" : selected ? "APPLYING..." : "TAP TO APPLY >",
                11,
                active
                        ? getColor(R.color.accent_coral)
                        : selected
                        ? getColor(R.color.accent_yellow)
                        : getColor(R.color.text_muted)
        );
''',
    '''        boolean queuedForWake = selected
                && !active
                && PebbleRuntimeService.isSelectionQueuedForWake();
        TextView action = pixelText(
                active
                        ? "ON AIR"
                        : queuedForWake
                        ? "QUEUED FOR WAKE"
                        : selected
                        ? "APPLYING..."
                        : "TAP TO APPLY >",
                11,
                active
                        ? getColor(R.color.accent_coral)
                        : selected
                        ? getColor(R.color.accent_yellow)
                        : getColor(R.color.text_muted)
        );
''',
    1
)
text, count = re.subn(
    r'    private void maybeRequestBackgroundSetup\(\) \{.*?\n\}\n\n@Override\npublic void onRequestPermissionsResult\(.*?\n\}\n',
    '    private void maybeRequestBackgroundSetup() {\n'
    '        getWindow().getDecorView().post(this::maybeShowBatteryPrompt);\n'
    '    }\n',
    text,
    count=1,
    flags=re.S
)
if count != 1:
    raise SystemExit("Notification permission block not found in MainActivity")
file.write_text(text)

changelog = Path("CHANGELOG_0.8.10.md")
text = changelog.read_text()
marker = "- Android 14+ foreground-service type and permission declarations are included.\n"
addition = marker + (
    "- Runtime mode selection now uses a custom Pebblehertz-styled dialog with Reliable visually recommended.\n"
    "- The service notification has no buttons, alerts or notification-permission prompt.\n"
    "- Reliable mode schedules a guarded restart after Titan 2 removes the app task.\n"
    "- Watchface changes during Schedule Sleep are queued and applied once after wake or charging.\n"
    "- Schedule Sleep sends only one QEMU pause command per sleep interval.\n"
)
if marker not in text:
    raise SystemExit("Changelog marker not found")
changelog.write_text(text.replace(marker, addition, 1))

Path(".github/workflows/stabilize-stage1.yml").unlink(missing_ok=True)
Path(".github/scripts/stabilize_stage1.py").unlink(missing_ok=True)
