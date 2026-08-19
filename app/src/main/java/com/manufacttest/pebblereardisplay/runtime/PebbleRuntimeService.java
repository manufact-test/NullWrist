package com.manufacttest.pebblereardisplay.runtime;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.data.WatchfaceThumbnailRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns one continuously running PebbleOS/QEMU instance independently from every Activity. */
public final class PebbleRuntimeService extends Service {
    private static final String ACTION_START =
            "com.manufacttest.pebblereardisplay.action.START_RUNTIME";
    private static final String ACTION_SELECT =
            "com.manufacttest.pebblereardisplay.action.SELECT_WATCHFACE";
    private static final String ACTION_RESTART =
            "com.manufacttest.pebblereardisplay.action.RESTART_RUNTIME";
    private static final String ACTION_RECOVER_TASK =
            "com.manufacttest.pebblereardisplay.action.RECOVER_AFTER_TASK_REMOVAL";

    public static final String ACTION_SELECTION_FAILED =
            "com.manufacttest.pebblereardisplay.action.SELECTION_FAILED";
    public static final String EXTRA_SELECTION_FAILURE = "selection_failure";

    private static final long THUMBNAIL_MIN_SETTLE_MILLIS = 4_500L;
    private static final long THUMBNAIL_MAX_SETTLE_MILLIS = 8_000L;
    private static final long THUMBNAIL_QUIET_MILLIS = 650L;
    private static final long SELECTION_DEBOUNCE_MILLIS = 180L;
    private static final long WATCHDOG_INTERVAL_MILLIS = 5_000L;
    private static final long RECOVERY_DELAY_MILLIS = 1_500L;
    private static final int STATE_RESET_FAILURE_THRESHOLD = 2;
    private static final int MAX_AUTOMATIC_FAILURES = 5;
    private static final String NOTIFICATION_CHANNEL_ID = "nullwrist_runtime";
    private static final int NOTIFICATION_ID = 168;
    private static final int RECOVERY_REQUEST_CODE = 810;

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile PebbleRuntimeService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger thumbnailGeneration = new AtomicInteger();
    private final AtomicInteger selectionRequest = new AtomicInteger();
    private final Handler serviceHandler = new Handler(Looper.getMainLooper());

    private volatile PebbleQemuProcess runtime;
    private volatile String status = "Starting PebbleOS…";
    private volatile String failure;
    private volatile boolean starting;
    private volatile boolean runtimeBusy;
    private volatile String activeStorageId;
    private volatile boolean foreground;
    private volatile long restartNotBeforeElapsed;
    private volatile int consecutiveFailures;
    private volatile boolean persistentRecoveryPerformed;
    private volatile boolean automaticRecoveryPaused;

    private final Runnable watchdogTick = new Runnable() {
        @Override
        public void run() {
            if (shouldHaveRuntime() && !automaticRecoveryPaused) {
                PebbleQemuProcess current = runtime;
                if (!starting
                        && (current == null || !current.isRunning())
                        && SystemClock.elapsedRealtime() >= restartNotBeforeElapsed) {
                    scheduleRuntime(false);
                }
            }
            serviceHandler.postDelayed(this, WATCHDOG_INTERVAL_MILLIS);
        }
    };

    private final Runnable selectionDebounce = this::dispatchLatestSelection;

    public static void start(Context context) {
        send(context, ACTION_START);
    }

    public static void select(Context context) {
        send(context, ACTION_SELECT);
    }

    public static void restart(Context context) {
        send(context, ACTION_RESTART);
    }

    public static boolean isActive() {
        PebbleRuntimeService current = instance;
        return current != null && (current.starting || current.runtime != null);
    }

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
        Context application = context.getApplicationContext();
        Intent intent = new Intent(application, PebbleRuntimeService.class).setAction(action);
        application.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        promoteToForeground();
        cancelTaskRemovalRecovery();
        serviceHandler.post(watchdogTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promoteToForeground();
        String action = intent == null ? ACTION_RECOVER_TASK : intent.getAction();

        // Explicit user/app requests may reopen a circuit that was intentionally paused after
        // repeated failures. A START_STICKY restart has a null Intent and must not do that.
        if (intent != null && (ACTION_RESTART.equals(action) || ACTION_SELECT.equals(action))) {
            resetRecoveryCircuit();
        } else if (intent != null
                && ACTION_START.equals(action)
                && automaticRecoveryPaused) {
            resetRecoveryCircuit();
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
    public void onTaskRemoved(Intent rootIntent) {
        scheduleTaskRemovalRecovery();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        generation.incrementAndGet();
        selectionRequest.incrementAndGet();
        thumbnailGeneration.incrementAndGet();
        serviceHandler.removeCallbacksAndMessages(null);
        if (shouldHaveRuntime()) {
            scheduleTaskRemovalRecovery();
        }
        executor.shutdownNow();
        thumbnailExecutor.shutdownNow();
        stopRuntime();
        demoteFromForeground();
        instance = null;
        notifyListeners();
        super.onDestroy();
    }

    private synchronized void scheduleRuntime(boolean forceRestart) {
        PebbleQemuProcess current = runtime;
        boolean healthyRuntime = current != null
                && current.isRunning()
                && activeStorageId != null
                && failure == null;

        if (starting) {
            notifyListeners();
            return;
        }
        if (!forceRestart && automaticRecoveryPaused) {
            notifyListeners();
            return;
        }
        if (!forceRestart && healthyRuntime) {
            notifyListeners();
            return;
        }
        if (!forceRestart && SystemClock.elapsedRealtime() < restartNotBeforeElapsed) {
            return;
        }

        int requestedGeneration = generation.incrementAndGet();
        boolean replaceExisting = forceRestart || current != null;
        starting = true;
        runtimeBusy = true;
        failure = null;
        activeStorageId = null;
        setStatus("Starting PebbleOS…");
        try {
            executor.execute(() -> runRuntime(requestedGeneration, replaceExisting));
        } catch (RejectedExecutionException ignored) {
            starting = false;
            runtimeBusy = false;
        }
    }

    private void scheduleSelection() {
        selectionRequest.incrementAndGet();
        serviceHandler.removeCallbacks(selectionDebounce);
        serviceHandler.postDelayed(selectionDebounce, SELECTION_DEBOUNCE_MILLIS);
    }

    private void dispatchLatestSelection() {
        int request = selectionRequest.get();
        int requestedGeneration = generation.get();
        try {
            executor.execute(() -> applyLatestSelection(requestedGeneration, request));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void applyLatestSelection(int requestedGeneration, int request) {
        if (requestedGeneration != generation.get() || request != selectionRequest.get()) {
            return;
        }

        PebbleQemuProcess current = runtime;
        if (current == null || !current.isRunning()) {
            scheduleRuntime(true);
            return;
        }

        runtimeBusy = true;
        SelectedWatchface previous = null;
        String previousId = activeStorageId;
        try {
            if (previousId != null) {
                try {
                    previous = watchfaceByStorageId(previousId);
                } catch (IOException missingPrevious) {
                    previous = null;
                }
            }
            SelectedWatchface selected = selectedWatchface();
            if (request != selectionRequest.get()) {
                return;
            }
            if (selected.metadata.getStorageId().equals(activeStorageId)) {
                failure = null;
                status = null;
                notifyListeners();
                return;
            }
            thumbnailGeneration.incrementAndGet();
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
        }
    }

    private void runRuntime(int requestedGeneration, boolean replaceExisting) {
        PebbleQemuProcess current = null;
        PowerManager.WakeLock startupWakeLock = acquireStartupWakeLock();
        try {
            if (replaceExisting) {
                discardRuntime(null);
            }
            ensureCurrent(requestedGeneration);

            // Clean up an orphan left behind if Android killed the Java/service process without
            // giving PebbleQemuProcess.stop() a chance to run.
            PebbleRuntimeRecovery.prepareBeforeStart(this);
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
            current.waitForFirmwareReady(40_000L);
            ensureCurrent(requestedGeneration);

            setStatus("Waiting for Pebble display…");
            if (!current.waitForFirstFrame(12_000L)) {
                Integer exitCode = current.exitCode();
                throw new IOException(exitCode == null
                        ? "PebbleOS did not produce a framebuffer"
                        : "PebbleOS stopped with code " + exitCode);
            }
            ensureCurrent(requestedGeneration);

            activateSelected(current, selectedWatchface());
            ensureCurrent(requestedGeneration);
            starting = false;
            consecutiveFailures = 0;
            restartNotBeforeElapsed = 0L;
            persistentRecoveryPerformed = false;
            automaticRecoveryPaused = false;
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

        int frameAfterConfirmation = current.readFrameSequence();
        boolean rendered = frameAfterConfirmation > 0
                && frameAfterConfirmation != frameBeforeLaunch;
        if (!rendered && !waitForFrameAdvance(
                current,
                frameAfterConfirmation,
                installed ? 8_000L : 5_000L
        )) {
            throw new IOException("Confirmed watchface did not render a framebuffer");
        }

        activeStorageId = selected.metadata.getStorageId();
        starting = false;
        status = null;
        failure = null;
        consecutiveFailures = 0;
        restartNotBeforeElapsed = 0L;
        persistentRecoveryPerformed = false;
        automaticRecoveryPaused = false;
        updateNotification();
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
            Thread.sleep(80L);
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
        try {
            thumbnailExecutor.execute(() -> captureThumbnailWhenReady(
                    current,
                    metadata,
                    token
            ));
        } catch (RejectedExecutionException ignored) {
        }
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
        WatchfaceRepository repository = new WatchfaceRepository(this);
        WatchfaceMetadata metadata = repository.findByStorageId(
                preferences.getSelectedWatchfaceId()
        );
        if (metadata == null) {
            List<WatchfaceMetadata> available = repository.loadAll();
            if (available.isEmpty()) {
                throw new IOException("No watchfaces are available");
            }
            metadata = available.get(0);
            preferences.setSelectedWatchfaceId(metadata.getStorageId());
        }
        File file = repository.fileFor(metadata);
        if (!file.isFile()) {
            throw new IOException("Selected PBW file is missing: " + metadata.getName());
        }
        return new SelectedWatchface(metadata, file);
    }

    private SelectedWatchface watchfaceByStorageId(String storageId) throws IOException {
        WatchfaceRepository repository = new WatchfaceRepository(this);
        WatchfaceMetadata metadata = repository.findByStorageId(storageId);
        if (metadata == null) {
            throw new IOException("Selected watchface is no longer available");
        }
        File file = repository.fileFor(metadata);
        if (!file.isFile()) {
            throw new IOException("Selected PBW file is missing: " + metadata.getName());
        }
        return new SelectedWatchface(metadata, file);
    }

    private boolean shouldHaveRuntime() {
        return new AppPreferences(this).hasSavedWatchfaceSelection();
    }

    private void reportFailure(Throwable error) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 20);

        if (!persistentRecoveryPerformed
                && consecutiveFailures >= STATE_RESET_FAILURE_THRESHOLD) {
            try {
                PebbleRuntimeRecovery.resetPersistentState(this);
                persistentRecoveryPerformed = true;
            } catch (IOException recoveryError) {
                error.addSuppressed(recoveryError);
            }
        }

        failure = error.getClass().getSimpleName() + ": " + safeMessage(error);
        if (consecutiveFailures >= MAX_AUTOMATIC_FAILURES) {
            automaticRecoveryPaused = true;
            restartNotBeforeElapsed = Long.MAX_VALUE;
            status = "PebbleOS recovery paused — reopen NullWrist to retry";
            updateNotification();
            notifyListeners();
            return;
        }

        long delay = RuntimeRestartBackoff.delayMillis(consecutiveFailures);
        restartNotBeforeElapsed = SystemClock.elapsedRealtime() + delay;
        status = persistentRecoveryPerformed
                ? "PebbleOS state repaired; retrying automatically"
                : "PebbleOS will recover automatically";
        updateNotification();
        notifyListeners();
        serviceHandler.postDelayed(() -> scheduleRuntime(false), delay);
    }

    private void resetRecoveryCircuit() {
        consecutiveFailures = 0;
        restartNotBeforeElapsed = 0L;
        persistentRecoveryPerformed = false;
        automaticRecoveryPaused = false;
    }

    private void setStatus(String value) {
        status = value;
        failure = null;
        updateNotification();
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

    private void scheduleTaskRemovalRecovery() {
        if (!shouldHaveRuntime()) {
            return;
        }
        PendingIntent pending = recoveryPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = getSystemService(AlarmManager.class);
        if (alarm != null) {
            alarm.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RECOVERY_DELAY_MILLIS,
                    pending
            );
        }
    }

    private void cancelTaskRemovalRecovery() {
        PendingIntent pending = recoveryPendingIntent(PendingIntent.FLAG_NO_CREATE);
        if (pending == null) {
            return;
        }
        AlarmManager alarm = getSystemService(AlarmManager.class);
        if (alarm != null) {
            alarm.cancel(pending);
        }
        pending.cancel();
    }

    private PendingIntent recoveryPendingIntent(int flags) {
        return PendingIntent.getForegroundService(
                this,
                RECOVERY_REQUEST_CODE,
                new Intent(this, PebbleRuntimeService.class).setAction(ACTION_RECOVER_TASK),
                flags | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void updateNotification() {
        if (!foreground) {
            promoteToForeground();
        }
    }

    private void promoteToForeground() {
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
                "NullWrist",
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
                .setContentTitle("NullWrist")
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
