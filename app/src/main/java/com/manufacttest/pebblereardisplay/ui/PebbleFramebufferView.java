package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.view.View;

import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PebbleFramebufferView extends View {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint();
    private final Bitmap bitmap = Bitmap.createBitmap(
            PebbleQemuProcess.WIDTH,
            PebbleQemuProcess.HEIGHT,
            Bitmap.Config.ARGB_8888
    );
    private final int[] readPixels = new int[PebbleQemuProcess.WIDTH * PebbleQemuProcess.HEIGHT];
    private final int[] pendingPixels = new int[PebbleQemuProcess.WIDTH * PebbleQemuProcess.HEIGHT];
    private final Object pixelLock = new Object();
    private final AtomicBoolean uiUpdateScheduled = new AtomicBoolean();

    private volatile PebbleQemuProcess runtime;
    private volatile boolean listening;
    private volatile FileDescriptor eventDescriptor;
    private Thread eventThread;
    private int lastSequence = -1;
    private int pendingGeneration;

    public PebbleFramebufferView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);
    }

    public void attach(PebbleQemuProcess runtime) {
        detach();
        this.runtime = runtime;
        lastSequence = -1;
        listening = true;
        Thread thread = new Thread(
                () -> runEventLoop(runtime),
                "PebbleFramebufferEvents"
        );
        thread.setDaemon(true);
        eventThread = thread;
        thread.start();
    }

    public void detach() {
        listening = false;
        runtime = null;
        FileDescriptor descriptor = eventDescriptor;
        eventDescriptor = null;
        if (descriptor != null && descriptor.valid()) {
            try {
                Os.close(descriptor);
            } catch (ErrnoException ignored) {
            }
        }
        Thread thread = eventThread;
        eventThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        lastSequence = -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    private void runEventLoop(PebbleQemuProcess attached) {
        publishLatestFrame(attached);
        FileDescriptor descriptor = null;
        try {
            descriptor = Os.open(
                    attached.getFrameEventFile().getAbsolutePath(),
                    OsConstants.O_RDWR | OsConstants.O_NONBLOCK,
                    0
            );
            eventDescriptor = descriptor;

            StructPollfd pollfd = new StructPollfd();
            pollfd.fd = descriptor;
            pollfd.events = (short) (OsConstants.POLLIN | OsConstants.POLLERR);
            StructPollfd[] pollfds = new StructPollfd[]{pollfd};
            byte[] signals = new byte[64];

            while (listening && runtime == attached && attached.isRunning()) {
                int ready = Os.poll(pollfds, 1_000);
                if (!listening || runtime != attached) {
                    break;
                }
                if (ready > 0 && (pollfd.revents & OsConstants.POLLIN) != 0) {
                    drainSignals(descriptor, signals);
                    publishLatestFrame(attached);
                } else if (ready == 0) {
                    publishLatestFrame(attached);
                }
            }
        } catch (ErrnoException error) {
            fallbackLoop(attached);
        } finally {
            if (eventDescriptor == descriptor) {
                eventDescriptor = null;
            }
            if (descriptor != null && descriptor.valid()) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    private void fallbackLoop(PebbleQemuProcess attached) {
        while (listening && runtime == attached && attached.isRunning()) {
            publishLatestFrame(attached);
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void drainSignals(FileDescriptor descriptor, byte[] buffer)
            throws ErrnoException {
        while (true) {
            try {
                int read = Os.read(descriptor, buffer, 0, buffer.length);
                if (read <= 0 || read < buffer.length) {
                    return;
                }
            } catch (InterruptedIOException interrupted) {
                return;
            } catch (ErrnoException error) {
                if (error.errno == OsConstants.EAGAIN) {
                    return;
                }
                throw error;
            }
        }
    }

    private void publishLatestFrame(PebbleQemuProcess attached) {
        int sequence = attached.readFrameSequence();
        if (sequence <= 0 || sequence == lastSequence) {
            return;
        }
        if (!attached.readFrame(readPixels)) {
            return;
        }
        lastSequence = sequence;
        synchronized (pixelLock) {
            System.arraycopy(readPixels, 0, pendingPixels, 0, readPixels.length);
            pendingGeneration++;
        }
        scheduleUiUpdate();
    }

    private void scheduleUiUpdate() {
        if (uiUpdateScheduled.compareAndSet(false, true)) {
            mainHandler.post(this::applyPendingFrame);
        }
    }

    private void applyPendingFrame() {
        int appliedGeneration;
        synchronized (pixelLock) {
            bitmap.setPixels(
                    pendingPixels,
                    0,
                    PebbleQemuProcess.WIDTH,
                    0,
                    0,
                    PebbleQemuProcess.WIDTH,
                    PebbleQemuProcess.HEIGHT
            );
            appliedGeneration = pendingGeneration;
        }
        invalidate();
        uiUpdateScheduled.set(false);
        synchronized (pixelLock) {
            if (pendingGeneration != appliedGeneration) {
                scheduleUiUpdate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableWidth = getWidth();
        int availableHeight = getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        float scale = Math.min(
                availableWidth / (float) PebbleQemuProcess.WIDTH,
                availableHeight / (float) PebbleQemuProcess.HEIGHT
        );
        int drawnWidth = Math.max(1, Math.round(PebbleQemuProcess.WIDTH * scale));
        int drawnHeight = Math.max(1, Math.round(PebbleQemuProcess.HEIGHT * scale));
        int left = (availableWidth - drawnWidth) / 2;
        int top = (availableHeight - drawnHeight) / 2;
        Rect destination = new Rect(left, top, left + drawnWidth, top + drawnHeight);
        canvas.drawBitmap(bitmap, null, destination, paint);
    }
}
