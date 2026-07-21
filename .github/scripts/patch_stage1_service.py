from pathlib import Path

PATH = Path("app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java")
text = PATH.read_text(encoding="utf-8")


def replace(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one service match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)


replace(
    "import android.app.Service;\n",
    "import android.app.Notification;\n"
    "import android.app.NotificationChannel;\n"
    "import android.app.NotificationManager;\n"
    "import android.app.PendingIntent;\n"
    "import android.app.Service;\n",
)
replace(
    "import android.content.IntentFilter;\n",
    "import android.content.IntentFilter;\nimport android.content.pm.ServiceInfo;\n",
)
replace(
    "import com.manufacttest.pebblereardisplay.data.AppPreferences;\n",
    "import com.manufacttest.pebblereardisplay.R;\n"
    "import com.manufacttest.pebblereardisplay.data.AppPreferences;\n",
)
replace(
    "    private static final String ACTION_REFRESH_POWER =\n"
    "            \"com.manufacttest.pebblereardisplay.action.REFRESH_POWER_POLICY\";\n",
    "    private static final String ACTION_REFRESH_POWER =\n"
    "            \"com.manufacttest.pebblereardisplay.action.REFRESH_POWER_POLICY\";\n"
    "    private static final String ACTION_APPLY_RUNTIME_MODE =\n"
    "            \"com.manufacttest.pebblereardisplay.action.APPLY_RUNTIME_MODE\";\n",
)
replace(
    "    private static final long LOW_BATTERY_AWAKE_MILLIS = 3_500L;\n",
    "    private static final long LOW_BATTERY_AWAKE_MILLIS = 3_500L;\n"
    "    private static final String NOTIFICATION_CHANNEL_ID = \"pebblehertz_runtime\";\n"
    "    private static final int NOTIFICATION_ID = 168;\n",
)
replace(
    "    private volatile boolean phoneChargerConnected;\n",
    "    private volatile boolean phoneChargerConnected;\n"
    "    private volatile boolean foreground;\n"
    "    private volatile String notificationText = \"Starting PebbleOS…\";\n",
)
replace(
    "    public static void refreshPowerPolicy(Context context) {\n"
    "        send(context, ACTION_REFRESH_POWER);\n"
    "    }\n",
    "    public static void refreshPowerPolicy(Context context) {\n"
    "        send(context, ACTION_REFRESH_POWER);\n"
    "    }\n\n"
    "    public static void applyRuntimeMode(Context context) {\n"
    "        send(context, ACTION_APPLY_RUNTIME_MODE);\n"
    "    }\n",
)
replace(
    "    private static void send(Context context, String action) {\n"
    "        Intent intent = new Intent(context, PebbleRuntimeService.class).setAction(action);\n"
    "        context.startService(intent);\n"
    "    }\n",
    "    private static void send(Context context, String action) {\n"
    "        Context application = context.getApplicationContext();\n"
    "        Intent intent = new Intent(application, PebbleRuntimeService.class).setAction(action);\n"
    "        boolean reliable = new AppPreferences(application).isReliableRuntime();\n"
    "        if (reliable && !ACTION_STOP.equals(action)) {\n"
    "            application.startForegroundService(intent);\n"
    "        } else {\n"
    "            application.startService(intent);\n"
    "        }\n"
    "    }\n",
)
replace(
    "        instance = this;\n        IntentFilter filter = new IntentFilter();\n",
    "        instance = this;\n"
    "        createNotificationChannel();\n"
    "        applyRuntimeModeInternal(notificationText);\n"
    "        IntentFilter filter = new IntentFilter();\n",
)
replace(
    "        if (ACTION_STOP.equals(action)) {\n"
    "            generation.incrementAndGet();\n"
    "            stopRuntime();\n"
    "            stopSelf();\n"
    "            return START_NOT_STICKY;\n"
    "        }\n"
    "        if (ACTION_SELECT.equals(action)) {\n",
    "        if (ACTION_STOP.equals(action)) {\n"
    "            generation.incrementAndGet();\n"
    "            stopRuntime();\n"
    "            demoteFromForeground();\n"
    "            stopSelf();\n"
    "            return START_NOT_STICKY;\n"
    "        }\n"
    "        if (ACTION_APPLY_RUNTIME_MODE.equals(action)) {\n"
    "            applyRuntimeModeInternal(notificationText);\n"
    "            notifyListeners();\n"
    "            return START_STICKY;\n"
    "        }\n"
    "        if (ACTION_SELECT.equals(action)) {\n",
)
replace(
    "        stopRuntime();\n        executor.shutdownNow();\n",
    "        stopRuntime();\n        demoteFromForeground();\n        executor.shutdownNow();\n",
)
replace(
    "    private void updateNotification(String text) {\n"
    "        // Runtime state is available in the app; Android shade notifications stay disabled.\n"
    "    }\n\n"
    "    private static String safeMessage(Throwable error) {\n",
    "    private void updateNotification(String text) {\n"
    "        notificationText = text == null || text.isBlank()\n"
    "                ? \"Pebble Time is running\"\n"
    "                : text;\n"
    "        if (!new AppPreferences(this).isReliableRuntime()) {\n"
    "            return;\n"
    "        }\n"
    "        if (!foreground) {\n"
    "            promoteToForeground(notificationText);\n"
    "            return;\n"
    "        }\n"
    "        NotificationManager manager = getSystemService(NotificationManager.class);\n"
    "        if (manager != null) {\n"
    "            manager.notify(NOTIFICATION_ID, buildNotification(notificationText));\n"
    "        }\n"
    "    }\n\n"
    "    private void applyRuntimeModeInternal(String text) {\n"
    "        if (new AppPreferences(this).isReliableRuntime()) {\n"
    "            promoteToForeground(text);\n"
    "        } else {\n"
    "            demoteFromForeground();\n"
    "        }\n"
    "    }\n\n"
    "    private void promoteToForeground(String text) {\n"
    "        notificationText = text == null || text.isBlank()\n"
    "                ? \"Pebble Time is running\"\n"
    "                : text;\n"
    "        Notification notification = buildNotification(notificationText);\n"
    "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {\n"
    "            startForeground(\n"
    "                    NOTIFICATION_ID,\n"
    "                    notification,\n"
    "                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE\n"
    "            );\n"
    "        } else {\n"
    "            startForeground(NOTIFICATION_ID, notification);\n"
    "        }\n"
    "        foreground = true;\n"
    "    }\n\n"
    "    private void demoteFromForeground() {\n"
    "        if (!foreground) {\n"
    "            return;\n"
    "        }\n"
    "        stopForeground(STOP_FOREGROUND_REMOVE);\n"
    "        foreground = false;\n"
    "    }\n\n"
    "    private void createNotificationChannel() {\n"
    "        NotificationManager manager = getSystemService(NotificationManager.class);\n"
    "        if (manager == null || manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {\n"
    "            return;\n"
    "        }\n"
    "        NotificationChannel channel = new NotificationChannel(\n"
    "                NOTIFICATION_CHANNEL_ID,\n"
    "                \"Pebblehertz runtime\",\n"
    "                NotificationManager.IMPORTANCE_LOW\n"
    "        );\n"
    "        channel.setDescription(\n"
    "                \"Keeps the selected Pebble watchface active on the rear display.\"\n"
    "        );\n"
    "        channel.setSound(null, null);\n"
    "        channel.enableVibration(false);\n"
    "        channel.enableLights(false);\n"
    "        channel.setShowBadge(false);\n"
    "        manager.createNotificationChannel(channel);\n"
    "    }\n\n"
    "    private Notification buildNotification(String text) {\n"
    "        Intent openIntent = new Intent(\n"
    "                this,\n"
    "                com.manufacttest.pebblereardisplay.ui.MainActivity.class\n"
    "        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n"
    "        PendingIntent open = PendingIntent.getActivity(\n"
    "                this,\n"
    "                0,\n"
    "                openIntent,\n"
    "                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE\n"
    "        );\n"
    "        Intent stopIntent = new Intent(this, PebbleRuntimeService.class)\n"
    "                .setAction(ACTION_STOP);\n"
    "        PendingIntent stop = PendingIntent.getService(\n"
    "                this,\n"
    "                1,\n"
    "                stopIntent,\n"
    "                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE\n"
    "        );\n"
    "        return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)\n"
    "                .setSmallIcon(R.drawable.ic_notification)\n"
    "                .setContentTitle(\"Pebblehertz is on air\")\n"
    "                .setContentText(text)\n"
    "                .setContentIntent(open)\n"
    "                .setCategory(Notification.CATEGORY_SERVICE)\n"
    "                .setVisibility(Notification.VISIBILITY_PUBLIC)\n"
    "                .setOngoing(true)\n"
    "                .setOnlyAlertOnce(true)\n"
    "                .setShowWhen(false)\n"
    "                .setSilent(true)\n"
    "                .addAction(R.drawable.ic_notification, \"Stop\", stop)\n"
    "                .build();\n"
    "    }\n\n"
    "    private static String safeMessage(Throwable error) {\n",
)

PATH.write_text(text, encoding="utf-8")
