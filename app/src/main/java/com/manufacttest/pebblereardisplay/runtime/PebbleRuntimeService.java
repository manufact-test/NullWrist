package com.manufacttest.pebblereardisplay.runtime;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;
import com.manufacttest.pebblereardisplay.ui.MainActivity;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns the native PebbleOS/QEMU process independently from any Activity or display surface. */
public final class PebbleRuntimeService extends Service {
    private static final String ACTION_START = "com.manufacttest.pebblereardisplay.action.START_RUNTIME";
    private static final String ACTION_RESTART = "com.manufacttest.pebblereardisplay.action.RESTART_RUNTIME";
    private static final String ACTION_STOP = "com.manufacttest.pebblereardisplay.action.STOP_RUNTIME";
    private static final String CHANNEL_ID = "pebble_runtime";
    private static final int NOTIFICATION_ID = 4102;

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile PebbleRuntimeService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();

    private volatile PebbleQemuProcess runtime;
    private volatile String status = "Starting PebbleOS…";
    private volatile String failure;
    private volatile boolean starting;

    public static void start(Context context) {
        send(context, ACTION_START);
    }

    public static void restart(Context context) {
        send(context, ACTION_RESTART);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, PebbleRuntimeService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isActive() {
        PebbleRuntimeService current = instance;
        return current != null && (current.starting || current.runtime != null);
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
        PebbleRuntimeService current = instance;
        if (current == null) {
            listener.onRuntimeState(null, "Starting PebbleOS…", null);
        } else {
            current.dispatch(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void send(Context context, String action) {
        Intent intent = new Intent(context, PebbleRuntimeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        promoteToForeground(buildNotification("Pebble watchface is starting"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRuntime();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        scheduleRuntime(ACTION_RESTART.equals(action));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        generation.incrementAndGet();
        stopRuntime();
        executor.shutdownNow();
        instance = null;
        notifyListeners();
        super.onDestroy();
    }

    private synchronized void scheduleRuntime(boolean forceRestart) {
        if (!forceRestart && (starting || runtime != null)) {
            notifyListeners();
            return;
        }
        int requestedGeneration = generation.incrementAndGet();
        starting = true;
        failure = null;
        setStatus("Starting PebbleOS…");
        executor.execute(() -> runRuntime(requestedGeneration, forceRestart));
    }

    private void runRuntime(int requestedGeneration, boolean forceRestart) {
        if (forceRestart) {
            stopRuntime();
        }
        if (requestedGeneration != generation.get()) {
            return;
        }

        PebbleQemuProcess current = new PebbleQemuProcess(this);
        runtime = current;
        notifyListeners();
        try {
            SelectedWatchface selected = selectedWatchface();
            current.start();
            notifyListeners();

            setStatus("Waiting for PebbleOS…");
            current.waitForFirmwareReady(35_000);
            ensureCurrent(requestedGeneration);

            if (!current.waitForFirstFrame(10_000)) {
                Integer exitCode = current.exitCode();
                throw new IOException(exitCode == null
                        ? "PebbleOS did not produce a framebuffer"
                        : "PebbleOS stopped with code " + exitCode);
            }
            ensureCurrent(requestedGeneration);

            setStatus("Connecting to PebbleOS…");
            current.installWatchface(
                    selected.file,
                    (message, sentBytes, totalBytes) -> setStatus(message)
            );
            ensureCurrent(requestedGeneration);

            Thread.sleep(700);
            starting = false;
            status = null;
            failure = null;
            updateNotification("Running " + selected.metadata.getName());
            notifyListeners();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            starting = false;
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            status = "Could not start selected watchface";
            updateNotification("Pebble runtime needs attention");
            notifyListeners();
        }
    }

    private void ensureCurrent(int requestedGeneration) throws InterruptedException {
        if (requestedGeneration != generation.get()) {
            throw new InterruptedException("Pebble runtime was replaced");
        }
    }

    private synchronized void stopRuntime() {
        PebbleQemuProcess current = runtime;
        runtime = null;
        starting = false;
        if (current != null) {
            current.stop();
        }
        notifyListeners();
    }

    private SelectedWatchface selectedWatchface() throws IOException {
        WatchfaceRepository repository = new WatchfaceRepository(this);
        AppPreferences preferences = new AppPreferences(this);
        String selectedId = preferences.getSelectedWatchfaceId();
        WatchfaceMetadata metadata = repository.findByStorageId(selectedId);
        if (metadata == null) {
            throw new IOException("No watchface is selected");
        }
        File file = repository.fileFor(metadata);
        if (!file.isFile()) {
            throw new IOException("Selected PBW file is missing: " + metadata.getName());
        }
        return new SelectedWatchface(metadata, file);
    }

    private void setStatus(String value) {
        status = value;
        failure = null;
        updateNotification(value == null ? "Pebble watchface is running" : value);
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : LISTENERS) {
            dispatch(listener);
        }
    }

    private void dispatch(Listener listener) {
        listener.onRuntimeState(runtime, status, failure);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Pebble rear display",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the Pebble watchface active on the Titan 2 rear display");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        PendingIntent openIntent = PendingIntent.getActivity(
                this,
                1,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent stopIntent = PendingIntent.getService(
                this,
                2,
                new Intent(this, PebbleRuntimeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Pebble Rear Display")
                .setContentText(text)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Stop", stopIntent).build())
                .build();
    }

    private void promoteToForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public interface Listener {
        void onRuntimeState(PebbleQemuProcess runtime, String status, String failure);
    }

    private static final class SelectedWatchface {
        final WatchfaceMetadata metadata;
        final File file;

        SelectedWatchface(WatchfaceMetadata metadata, File file) {
            this.metadata = metadata;
            this.file = file;
        }
    }
}
