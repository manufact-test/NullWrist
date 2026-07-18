package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class QemuBinaryProbeActivity extends Activity {
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        executor.execute(this::runProbe);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private ScrollView buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("NATIVE QEMU PROBE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        output = new TextView(this);
        output.setText("Locating the bundled arm64 QEMU binary…");
        output.setTextColor(Color.LTGRAY);
        output.setTextSize(13);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        root.addView(output, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private void runProbe() {
        StringBuilder report = new StringBuilder();
        try {
            File nativeDirectory = new File(getApplicationInfo().nativeLibraryDir);
            File qemu = new File(nativeDirectory, "libpebble_qemu_exec.so");

            report.append("ABI directory: ")
                    .append(nativeDirectory.getAbsolutePath())
                    .append('\n');
            report.append("Binary: ")
                    .append(qemu.getAbsolutePath())
                    .append('\n');
            report.append("Exists: ").append(qemu.isFile()).append('\n');
            report.append("Size: ").append(qemu.isFile() ? qemu.length() : 0).append(" bytes\n");
            report.append("Executable: ").append(qemu.canExecute()).append("\n\n");

            if (!qemu.isFile()) {
                report.append("QEMU NOT PACKAGED\n")
                        .append("The native QEMU build has not been pinned into this APK yet.");
                publish(report.toString());
                return;
            }

            CommandResult version = execute(qemu, "--version");
            report.append("$ qemu-system-arm --version\n")
                    .append(version.output)
                    .append("\nExit: ").append(version.exitCode)
                    .append(" · timed out: ").append(version.timedOut)
                    .append("\n\n");

            CommandResult machines = execute(qemu, "-machine", "help");
            boolean hasBasalt = machines.output.contains("pebble-snowy-bb");
            boolean hasEmery = machines.output.contains("pebble-snowy-emery-bb");
            boolean hasChalk = machines.output.contains("pebble-s4-bb");

            report.append("$ qemu-system-arm -machine help\n")
                    .append(machines.output)
                    .append("\nExit: ").append(machines.exitCode)
                    .append(" · timed out: ").append(machines.timedOut)
                    .append("\n\nPebble machines:\n")
                    .append("Basalt: ").append(hasBasalt).append('\n')
                    .append("Emery: ").append(hasEmery).append('\n')
                    .append("Chalk: ").append(hasChalk).append("\n\n");

            if (version.exitCode == 0 && machines.exitCode == 0 && hasBasalt) {
                report.append("NATIVE QEMU OK");
            } else {
                report.append("NATIVE QEMU TEST FAILED");
            }
        } catch (Throwable throwable) {
            report.append("PROBE CRASHED\n")
                    .append(throwable.getClass().getSimpleName())
                    .append(": ")
                    .append(throwable.getMessage());
        }
        publish(report.toString());
    }

    private CommandResult execute(File executable, String... arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        command.addAll(Arrays.asList(arguments));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(getCacheDir());
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", getFilesDir().getAbsolutePath());
        builder.environment().put("TMPDIR", getCacheDir().getAbsolutePath());

        Process process = builder.start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }

        String commandOutput;
        try (InputStream stream = process.getInputStream()) {
            commandOutput = readLimited(stream);
        }
        int exitCode = finished ? process.exitValue() : -1;
        return new CommandResult(exitCode, !finished, commandOutput.trim());
    }

    private String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int remaining = MAX_OUTPUT_BYTES - total;
            if (remaining <= 0) {
                outputStream.write("\n[output truncated]".getBytes(StandardCharsets.UTF_8));
                break;
            }
            int accepted = Math.min(read, remaining);
            outputStream.write(buffer, 0, accepted);
            total += accepted;
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private void publish(String value) {
        runOnUiThread(() -> output.setText(value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CommandResult {
        final int exitCode;
        final boolean timedOut;
        final String output;

        CommandResult(int exitCode, boolean timedOut, String output) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.output = output;
        }
    }
}
