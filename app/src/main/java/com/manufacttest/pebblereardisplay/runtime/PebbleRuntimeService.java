package com.manufacttest.pebblereardisplay.runtime;

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
import com.manufacttest.pebblereardisplay.data.WatchfaceThumbnailRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;
import com.manufacttest.pebblereardisplay.ui.MainActivity;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns the native PebbleOS/QEMU process independently from any Activity or display surface. */
public final class PebbleRuntimeService extends Service {
    private static final String ACTION_START =
            "com.manufacttest.pebblereardisplay.action.START_RUNTIME";
    private static final String ACTION_SELECT =
            "com.manufacttest.pebblereardisplay.action.SELECT_WATCHFACE";
    private static final String ACTION_RESTART =
            "com.manufacttest.pebblereardisplay.action.RESTART_RUNTIME";
    private static final String ACTION_STOP =
            "com.manufacttest.pebblereardisplay.action.STOP_RUNTIME";
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
    private volatile String activeStorageId;

    public static void start(Context context) {
        send(context, ACTION_START);
    }

    /** Applies the selected PBW to the already-running PebbleOS whenever possible. */
    public static void select(Context context) {
        send(context, ACTION_SELECT);
    }

    /** Explicitly replaces the emulator process while retaining persistent SPI flash. */
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

    /** Returns the face acknowledged by the runtime, not merely the face selected in the UI. */
    public static String getActiveStorageId() {
        PebbleRuntimeService current = instance;
        return current == null ? null : current.activeStorageId;
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
        promoteToForeground(buildNotification("Pebble Time is starting"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            generation.incrementAndGet();
            stopRuntime();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SELECT.equals(action)) {
            scheduleSelection();
        } else {
            scheduleRuntime(ACTION_RESTART.equals(action));
        }
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
        PebbleQemuProcess current = runtime;
        boolean healthyRuntime = current != null
                && current.isRunning()
                && failure == null;

        if (starting) {
            notifyListeners();
            return;
        }
        if (!forceRestart && healthyRuntime) {
            notifyListeners();
            return;
        }

        int requestedGeneration = generation.incrementAndGet();
        boolean replaceExisting = forceRestart || current != null;
        starting = true;
        failure = null;
        activeStorageId = null;
        setStatus("Starting PebbleOS…");
        executor.execute(() -> runRuntime(requestedGeneration, replaceExisting));
    }

    private void scheduleSelection() {
        int requestedGeneration = generation.get();
        executor.execute(() -> applyLatestSelection(requestedGeneration));
    }

    private void applyLatestSelection(int requestedGeneration) {
        if (requestedGeneration != generation.get()) {
            return;
        }

        PebbleQemuProcess current = runtime;
        if (current == null || !current.isRunning()) {
            scheduleRuntime(false);
            return;
        }

        try {
            SelectedWatchface selected = selectedWatchface();
            if (selected.metadata.getStorageId().equals(activeStorageId)) {
                return;
            }
            activateSelected(current, selected);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failAndDiscardRuntime(current, interrupted);
        } catch (Throwable error) {
            failAndDiscardRuntime(current, error);
        }
    }

    private void runRuntime(int requestedGeneration, boolean replaceExisting) {
        PebbleQemuProcess current = null;
        try {
            if (replaceExisting) {
                discardRuntime(null);
            }
            ensureCurrent(requestedGeneration);

            current = new PebbleQemuProcess(this);
            synchronized (this) {
                ensureCurrent(requestedGeneration);
                runtime = current;
                activeStorageId = null;
            }
            notifyListeners();

            current.start();
            ensureCurrent(requestedGeneration);
            notifyListeners();

            setStatus("Connecting to PebbleOS…");
            current.waitForFirmwareReady(40_000);
            ensureCurrent(requestedGeneration);

            setStatus("Waiting for Pebble display…");
            if (!current.waitForFirstFrame(12_000)) {
                Integer exitCode = current.exitCode();
                throw new IOException(exitCode == null
                        ? "PebbleOS did not produce a framebuffer"
                        : "PebbleOS stopped with code " + exitCode);
            }
            ensureCurrent(requestedGeneration);

            activateSelected(current, selectedWatchface());
            ensureCurrent(requestedGeneration);
            starting = false;
        } catch (CancellationException cancelled) {
            discardRuntime(current);
        } catch (InterruptedException interrupted) {
            discardRuntime(current);
            if (requestedGeneration == generation.get()) {
                starting = false;
                reportFailure(interrupted);
            }
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            discardRuntime(current);
            if (requestedGeneration == generation.get()) {
                starting = false;
                reportFailure(error);
            }
        }
    }

    private void activateSelected(PebbleQemuProcess current, SelectedWatchface selected)
            throws IOException, InterruptedException {
        setStatus("Launching " + selected.metadata.getName() + "…");
        int frameBeforeLaunch = current.readFrameSequence();
        boolean installed = current.activateWatchface(
                selected.file,
                new InstalledWatchfaceRegistry(this),
                (message, sentBytes, totalBytes) -> setStatus(message)
        );

        setStatus("Waiting for watchface frame…");
        waitForFrameAdvance(current, frameBeforeLaunch, installed ? 6_000 : 4_000);
        captureThumbnailIfNeeded(current, selected.metadata);

        activeStorageId = selected.metadata.getStorageId();
        starting = false;
        status = null;
        failure = null;
        updateNotification("Running " + selected.metadata.getName());
        notifyListeners();
    }

    private static void waitForFrameAdvance(
            PebbleQemuProcess current,
            int previousSequence,
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            int sequence = current.readFrameSequence();
            if (sequence > 0 && sequence != previousSequence) {
                return;
            }
            if (!current.isRunning()) {
                return;
            }
            Thread.sleep(80);
        }
    }

    private void captureThumbnailIfNeeded(
            PebbleQemuProcess current,
            WatchfaceMetadata metadata
    ) {
        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);
        if (thumbnails.hasThumbnail(metadata) || !thumbnails.capture(current, metadata)) {
            return;
        }
        sendBroadcast(new Intent(WatchfaceThumbnailRepository.ACTION_THUMBNAIL_UPDATED)
                .setPackage(getPackageName())
                .putExtra(
                        WatchfaceThumbnailRepository.EXTRA_STORAGE_ID,
                        metadata.getStorageId()
                ));
    }

    private void ensureCurrent(int requestedGeneration) {
        if (requestedGeneration != generation.get()) {
            throw new CancellationException("Pebble runtime was replaced");
        }
    }

    private void failAndDiscardRuntime(PebbleQemuProcess current, Throwable error) {
        discardRuntime(current);
        starting = false;
        reportFailure(error);
    }

    /**
     * Stops the supplied runtime. When expected is null, whichever runtime is currently attached is
     * discarded. A newer runtime is never cleared by an older failing task.
     */
    private void discardRuntime(PebbleQemuProcess expected) {
        PebbleQemuProcess toStop;
        synchronized (this) {
            if (expected != null && runtime != expected) {
                toStop = expected;
            } else {
                toStop = runtime;
                runtime = null;
                activeStorageId = null;
            }
        }
        if (toStop != null) {
            toStop.stop();
        }
        notifyListeners();
    }

    private synchronized void stopRuntime() {
        PebbleQemuProcess current = runtime;
        runtime = null;
        activeStorageId = null;
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

    private void reportFailure(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        failure = error.getClass().getSimpleName() + ": " + message;
        status = "Could not start selected watchface";
        updateNotification("Pebble Time needs attention");
        notifyListeners();
    }

    private void setStatus(String value) {
        status = value;
        failure = null;
        updateNotification(value == null ? "Pebble Time is running" : value);
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
                "Pebblehertz runtime",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(
                "Keeps the selected Pebble Time face active on the Titan 2 rear display"
        );
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
                .setContentTitle(getString(R.string.app_name))
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
