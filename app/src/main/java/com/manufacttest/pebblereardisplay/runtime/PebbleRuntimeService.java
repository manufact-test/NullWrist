package com.manufacttest.pebblereardisplay.runtime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

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
    private static final String ACTION_REFRESH_POWER =
            "com.manufacttest.pebblereardisplay.action.REFRESH_POWER_POLICY";

    public static final String ACTION_SELECTION_FAILED =
            "com.manufacttest.pebblereardisplay.action.SELECTION_FAILED";
    public static final String EXTRA_SELECTION_FAILURE = "selection_failure";

    private static final String CHANNEL_ID = "pebble_runtime";
    private static final int NOTIFICATION_ID = 4102;
    private static final long THUMBNAIL_MIN_SETTLE_MILLIS = 4_500L;
    private static final long THUMBNAIL_MAX_SETTLE_MILLIS = 8_000L;
    private static final long THUMBNAIL_QUIET_MILLIS = 650L;
    private static final long LOW_BATTERY_AWAKE_MILLIS = 3_500L;

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile PebbleRuntimeService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger thumbnailGeneration = new AtomicInteger();
    private final Handler policyHandler = new Handler(Looper.getMainLooper());

    private volatile PebbleQemuProcess runtime;
    private volatile String status = "Starting PebbleOS…";
    private volatile String failure;
    private volatile boolean starting;
    private volatile boolean runtimeBusy;
    private volatile String activeStorageId;
    private volatile RuntimePowerPolicy.Mode powerMode = RuntimePowerPolicy.Mode.RUNNING;

    private final Runnable policyTick = new Runnable() {
        @Override
        public void run() {
            applyPowerPolicy();
            policyHandler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable lowBatteryPause = () -> {
        if (powerMode != RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE || runtimeBusy) {
            return;
        }
        PebbleQemuProcess current = runtime;
        if (current != null && current.isRunning()) {
            try {
                current.pause();
                updateNotification("Battery saver · updates each minute");
                notifyListeners();
            } catch (IOException error) {
                reportFailure(error);
            }
        }
    };

    private final Runnable lowBatteryPulse = () -> {
        if (powerMode != RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE || runtimeBusy) {
            return;
        }
        PebbleQemuProcess current = runtime;
        if (current == null || !current.isRunning()) {
            return;
        }
        try {
            current.resume();
            policyHandler.removeCallbacks(lowBatteryPause);
            policyHandler.postDelayed(lowBatteryPause, LOW_BATTERY_AWAKE_MILLIS);
        } catch (IOException error) {
            reportFailure(error);
        }
        scheduleNextLowBatteryPulse();
    };

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            policyHandler.removeCallbacks(policyTick);
            policyHandler.post(policyTick);
        }
    };

    public static void start(Context context) {
        send(context, ACTION_START);
    }

    public static void select(Context context) {
        send(context, ACTION_SELECT);
    }

    public static void restart(Context context) {
        send(context, ACTION_RESTART);
    }

    public static void refreshPowerPolicy(Context context) {
        send(context, ACTION_REFRESH_POWER);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, PebbleRuntimeService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isActive() {
        PebbleRuntimeService current = instance;
        return current != null && (current.starting || current.runtime != null);
    }

    public static String getActiveStorageId() {
        PebbleRuntimeService current = instance;
        return current == null ? null : current.activeStorageId;
    }

    public static String getPowerModeLabel() {
        PebbleRuntimeService current = instance;
        if (current == null) {
            return null;
        }
        if (current.powerMode == RuntimePowerPolicy.Mode.SCHEDULED_FREEZE) {
            return "SCHEDULE SLEEP";
        }
        if (current.powerMode == RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE) {
            return "BATTERY SAVER";
        }
        return null;
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
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerReceiver, filter);
        }
        policyHandler.post(policyTick);
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
        } else if (ACTION_REFRESH_POWER.equals(action)) {
            applyPowerPolicy();
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
        thumbnailGeneration.incrementAndGet();
        policyHandler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(powerReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        stopRuntime();
        executor.shutdownNow();
        thumbnailExecutor.shutdownNow();
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
            applyPowerPolicy();
            notifyListeners();
            return;
        }

        int requestedGeneration = generation.incrementAndGet();
        boolean replaceExisting = forceRestart || current != null;
        starting = true;
        runtimeBusy = true;
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

        runtimeBusy = true;
        SelectedWatchface previous = null;
        String previousId = activeStorageId;
        try {
            if (previousId != null) {
                previous = watchfaceByStorageId(previousId);
            }
            SelectedWatchface selected = selectedWatchface();
            if (selected.metadata.getStorageId().equals(activeStorageId)) {
                return;
            }
            // Cancel a pending capture before the framebuffer starts showing install/launch frames.
            thumbnailGeneration.incrementAndGet();
            current.resume();
            activateSelected(current, selected);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!restorePrevious(current, previous, interrupted)) {
                failAndDiscardRuntime(current, interrupted);
            }
        } catch (Throwable error) {
            if (!restorePrevious(current, previous, error)) {
                failAndDiscardRuntime(current, error);
            }
        } finally {
            runtimeBusy = false;
            policyHandler.post(this::applyPowerPolicy);
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
        } finally {
            runtimeBusy = false;
            policyHandler.post(this::applyPowerPolicy);
        }
    }

    private void activateSelected(PebbleQemuProcess current, SelectedWatchface selected)
            throws IOException, InterruptedException {
        status = null;
        failure = null;
        notifyListeners();

        int frameBeforeLaunch = current.readFrameSequence();
        boolean installed = current.activateWatchface(
                selected.file,
                new InstalledWatchfaceRegistry(this),
                null
        );

        // AppRunState has already confirmed the exact UUID. The framebuffer is now only a
        // rendering check, never the command acknowledgement.
        int frameAfterConfirmation = current.readFrameSequence();
        boolean rendered = frameAfterConfirmation > 0
                && frameAfterConfirmation != frameBeforeLaunch;
        if (!rendered && !waitForFrameAdvance(
                current,
                frameAfterConfirmation,
                installed ? 8_000 : 5_000
        )) {
            throw new IOException("Confirmed watchface did not render a framebuffer");
        }

        activeStorageId = selected.metadata.getStorageId();
        starting = false;
        status = null;
        failure = null;
        updateNotification("Running " + selected.metadata.getName());
        notifyListeners();
        scheduleThumbnailCapture(current, selected.metadata);
    }

    private boolean restorePrevious(
            PebbleQemuProcess current,
            SelectedWatchface previous,
            Throwable originalError
    ) {
        if (previous == null || current == null || !current.isRunning()) {
            return false;
        }
        try {
            current.resume();
            activateSelected(current, previous);
            new AppPreferences(this).setSelectedWatchfaceId(
                    previous.metadata.getStorageId()
            );
            sendBroadcast(new Intent(ACTION_SELECTION_FAILED)
                    .setPackage(getPackageName())
                    .putExtra(
                            EXTRA_SELECTION_FAILURE,
                            "Could not apply the selected PBW. Restored "
                                    + previous.metadata.getName()
                                    + ". "
                                    + safeMessage(originalError)
                    ));
            return true;
        } catch (Throwable restoreError) {
            return false;
        }
    }

    private static boolean waitForFrameAdvance(
            PebbleQemuProcess current,
            int previousSequence,
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            int sequence = current.readFrameSequence();
            if (sequence > 0 && sequence != previousSequence) {
                return true;
            }
            if (!current.isRunning()) {
                return false;
            }
            Thread.sleep(80);
        }
        return false;
    }

    private void scheduleThumbnailCapture(
            PebbleQemuProcess current,
            WatchfaceMetadata metadata
    ) {
        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);
        int token = thumbnailGeneration.incrementAndGet();
        if (thumbnails.hasCurrentThumbnail(metadata)) {
            return;
        }
        thumbnailExecutor.execute(() -> captureThumbnailWhenReady(
                current,
                metadata,
                token
        ));
    }

    private void captureThumbnailWhenReady(
            PebbleQemuProcess current,
            WatchfaceMetadata metadata,
            int token
    ) {
        try {
            if (!waitForThumbnailReady(current, token)) {
                return;
            }
            if (token != thumbnailGeneration.get()
                    || current != runtime
                    || !metadata.getStorageId().equals(activeStorageId)) {
                return;
            }
            WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);
            if (!current.isRunning() || !thumbnails.capture(current, metadata)) {
                return;
            }
            sendBroadcast(new Intent(WatchfaceThumbnailRepository.ACTION_THUMBNAIL_UPDATED)
                    .setPackage(getPackageName())
                    .putExtra(
                            WatchfaceThumbnailRepository.EXTRA_STORAGE_ID,
                            metadata.getStorageId()
                    ));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean waitForThumbnailReady(PebbleQemuProcess current, int token)
            throws InterruptedException {
        long started = System.nanoTime();
        long minimumDeadline = started + TimeUnit.MILLISECONDS.toNanos(
                THUMBNAIL_MIN_SETTLE_MILLIS
        );
        long maximumDeadline = started + TimeUnit.MILLISECONDS.toNanos(
                THUMBNAIL_MAX_SETTLE_MILLIS
        );
        while (thumbnailIsCurrent(current, token) && System.nanoTime() < minimumDeadline) {
            Thread.sleep(100L);
        }
        if (!thumbnailIsCurrent(current, token)) {
            return false;
        }

        int lastSequence = current.readFrameSequence();
        long lastFrameChange = System.nanoTime();
        long quietNanos = TimeUnit.MILLISECONDS.toNanos(THUMBNAIL_QUIET_MILLIS);
        while (thumbnailIsCurrent(current, token) && System.nanoTime() < maximumDeadline) {
            int sequence = current.readFrameSequence();
            long now = System.nanoTime();
            if (sequence != lastSequence) {
                lastSequence = sequence;
                lastFrameChange = now;
            } else if (now - lastFrameChange >= quietNanos) {
                return true;
            }
            Thread.sleep(80L);
        }
        return thumbnailIsCurrent(current, token);
    }

    private boolean thumbnailIsCurrent(PebbleQemuProcess current, int token) {
        return token == thumbnailGeneration.get()
                && current == runtime
                && current.isRunning();
    }

    private void applyPowerPolicy() {
        RuntimePowerPolicy.Snapshot snapshot = RuntimePowerPolicy.evaluate(
                this,
                new AppPreferences(this)
        );
        RuntimePowerPolicy.Mode previousMode = powerMode;
        powerMode = snapshot.mode;
        policyHandler.removeCallbacks(lowBatteryPause);
        policyHandler.removeCallbacks(lowBatteryPulse);

        PebbleQemuProcess current = runtime;
        if (runtimeBusy || current == null || !current.isRunning()) {
            notifyListeners();
            return;
        }

        try {
            if (snapshot.mode == RuntimePowerPolicy.Mode.RUNNING) {
                current.resume();
                if (previousMode != snapshot.mode) {
                    updateNotification(activeStorageId == null
                            ? "Pebble Time is running"
                            : "Running selected watchface");
                }
            } else if (snapshot.mode == RuntimePowerPolicy.Mode.SCHEDULED_FREEZE) {
                current.pause();
                AppPreferences preferences = new AppPreferences(this);
                updateNotification(
                        "Sleeping until "
                                + AppPreferences.formatMinutes(
                                preferences.getSleepEndMinutes()
                        )
                );
            } else {
                current.resume();
                policyHandler.postDelayed(lowBatteryPause, LOW_BATTERY_AWAKE_MILLIS);
                scheduleNextLowBatteryPulse();
                updateNotification("Battery saver · updates each minute");
            }
            status = null;
            failure = null;
            notifyListeners();
        } catch (IOException error) {
            reportFailure(error);
        }
    }

    private void scheduleNextLowBatteryPulse() {
        long now = System.currentTimeMillis();
        long nextMinute = 60_000L - (now % 60_000L);
        long delay = Math.max(1_000L, nextMinute - 1_000L);
        policyHandler.removeCallbacks(lowBatteryPulse);
        policyHandler.postDelayed(lowBatteryPulse, delay);
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
        runtimeBusy = false;
        if (current != null) {
            current.stop();
        }
        notifyListeners();
    }

    private SelectedWatchface selectedWatchface() throws IOException {
        AppPreferences preferences = new AppPreferences(this);
        return watchfaceByStorageId(preferences.getSelectedWatchfaceId());
    }

    private SelectedWatchface watchfaceByStorageId(String storageId) throws IOException {
        WatchfaceRepository repository = new WatchfaceRepository(this);
        WatchfaceMetadata metadata = repository.findByStorageId(storageId);
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
        failure = error.getClass().getSimpleName() + ": " + safeMessage(error);
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

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
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
