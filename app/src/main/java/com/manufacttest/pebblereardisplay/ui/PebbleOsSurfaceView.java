package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;
import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Passive, full-surface PebbleOS renderer used by the rear display and preview. */
public final class PebbleOsSurfaceView extends FrameLayout {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PebbleFramebufferView framebufferView;
    private final TextView statusView;

    private volatile PebbleQemuProcess runtime;
    private volatile boolean started;
    private volatile boolean released;

    public PebbleOsSurfaceView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setClickable(false);
        setFocusable(false);

        framebufferView = new PebbleFramebufferView(context);
        addView(framebufferView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        statusView = new TextView(context);
        statusView.setText("Starting PebbleOS…");
        statusView.setTextColor(Color.WHITE);
        statusView.setShadowLayer(5f, 0f, 1f, Color.BLACK);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(24), dp(24), dp(24), dp(24));
        addView(statusView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startRuntime();
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        framebufferView.detach();
        PebbleQemuProcess current = runtime;
        runtime = null;
        if (current != null) {
            current.stop();
        }
        executor.shutdownNow();
    }

    private synchronized void startRuntime() {
        if (started || released) {
            return;
        }
        started = true;
        executor.execute(this::runPebbleOs);
    }

    private void runPebbleOs() {
        PebbleQemuProcess current = new PebbleQemuProcess(getContext());
        runtime = current;
        try {
            SelectedWatchface selected = selectedWatchface();
            showStatus("Starting PebbleOS…");
            current.start();
            if (released) {
                current.stop();
                return;
            }

            postToUi(() -> framebufferView.attach(current));
            showStatus("Waiting for PebbleOS…");
            current.waitForFirmwareReady(35_000);
            if (released) {
                current.stop();
                return;
            }

            boolean receivedFrame = current.waitForFirstFrame(10_000);
            if (released) {
                current.stop();
                return;
            }
            if (!receivedFrame) {
                Integer exitCode = current.exitCode();
                String message = exitCode == null
                        ? "PebbleOS did not produce a framebuffer"
                        : "PebbleOS stopped with code " + exitCode;
                String log = current.readLogTail(4 * 1024);
                if (!log.isEmpty()) {
                    message += "\n\n" + log;
                }
                showFailure(message);
                return;
            }

            showStatus("Connecting to PebbleOS…");
            current.installWatchface(
                    selected.file,
                    (message, sentBytes, totalBytes) -> showStatus(message)
            );
            if (released) {
                current.stop();
                return;
            }

            Thread.sleep(700);
            postToUi(() -> statusView.setVisibility(View.GONE));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            String message = "Could not start selected watchface\n"
                    + error.getClass().getSimpleName() + ": " + error.getMessage();
            String log = current.readLogTail(4 * 1024);
            if (!log.isEmpty()) {
                message += "\n\n" + log;
            }
            showFailure(message);
        }
    }

    private SelectedWatchface selectedWatchface() throws IOException {
        WatchfaceRepository repository = new WatchfaceRepository(getContext());
        AppPreferences preferences = new AppPreferences(getContext());
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

    private void showStatus(String message) {
        postToUi(() -> {
            statusView.setText(message);
            statusView.setVisibility(View.VISIBLE);
        });
    }

    private void showFailure(String message) {
        postToUi(() -> {
            statusView.setText(message);
            statusView.setVisibility(View.VISIBLE);
        });
    }

    private void postToUi(Runnable action) {
        if (!released) {
            post(action);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
