package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PebbleOsProbeActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PebbleQemuProcess runtime;
    private PebbleFramebufferView framebufferView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        executor.execute(this::startPebbleOs);
    }

    @Override
    protected void onDestroy() {
        if (framebufferView != null) {
            framebufferView.detach();
        }
        if (runtime != null) {
            runtime.stop();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("PEBBLEOS · BASALT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        statusView = new TextView(this);
        statusView.setText("Preparing official Pebble firmware…");
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(13);
        statusView.setGravity(Gravity.CENTER_HORIZONTAL);
        statusView.setPadding(0, dp(8), 0, dp(10));
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        framebufferView = new PebbleFramebufferView(this);
        root.addView(framebufferView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return root;
    }

    private void startPebbleOs() {
        runtime = new PebbleQemuProcess(this);
        try {
            publishStatus("Starting native QEMU and PebbleOS…");
            runtime.start();
            runOnUiThread(() -> framebufferView.attach(runtime));

            boolean receivedFrame = runtime.waitForFirstFrame(30_000);
            if (receivedFrame) {
                publishStatus("REAL PEBBLEOS FRAMEBUFFER · 144×168 · QEMU RUNNING");
                return;
            }

            Integer exitCode = runtime.exitCode();
            StringBuilder failure = new StringBuilder("PebbleOS did not produce a frame");
            if (exitCode != null) {
                failure.append(" · QEMU exit ").append(exitCode);
            } else {
                failure.append(" · timed out after 30 s");
            }
            String log = runtime.readLogTail(8 * 1024);
            if (!log.isEmpty()) {
                failure.append("\n\n").append(log);
            }
            publishStatus(failure.toString());
        } catch (Throwable error) {
            String log = runtime == null ? "" : runtime.readLogTail(8 * 1024);
            String message = "PEBBLEOS START FAILED\n"
                    + error.getClass().getSimpleName() + ": " + error.getMessage();
            if (!log.isEmpty()) {
                message += "\n\n" + log;
            }
            publishStatus(message);
        }
    }

    private void publishStatus(String message) {
        runOnUiThread(() -> statusView.setText(message));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
