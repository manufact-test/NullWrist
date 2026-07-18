package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PebbleQemuProcess {
    public static final int WIDTH = 144;
    public static final int HEIGHT = 168;

    private static final int HEADER_BYTES = 64;
    private static final int FRAME_BYTES = WIDTH * HEIGHT;
    private static final int MAGIC = 0x50424642; // PBFB
    private static final String ASSET_ROOT = "pebble/basalt/";

    private final Context context;
    private final File runtimeDirectory;
    private final File microFlash;
    private final File spiFlash;
    private final File framebuffer;
    private final File logFile;
    private Process process;

    public PebbleQemuProcess(Context context) {
        this.context = context.getApplicationContext();
        runtimeDirectory = new File(this.context.getFilesDir(), "pebble-runtime/basalt");
        microFlash = new File(runtimeDirectory, "qemu_micro_flash.bin");
        spiFlash = new File(runtimeDirectory, "qemu_spi_flash.bin");
        framebuffer = new File(runtimeDirectory, "framebuffer.bin");
        logFile = new File(runtimeDirectory, "qemu.log");
    }

    public synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }
        prepareFiles();

        File qemu = new File(context.getApplicationInfo().nativeLibraryDir, "libpebble_qemu_exec.so");
        if (!qemu.isFile()) {
            throw new IOException("Bundled QEMU binary is missing: " + qemu);
        }
        if (!qemu.canExecute()) {
            throw new IOException("Bundled QEMU binary is not executable: " + qemu);
        }

        List<String> command = new ArrayList<>();
        command.add(qemu.getAbsolutePath());
        command.add("-rtc");
        command.add("base=localtime");
        command.add("-serial");
        command.add("null");
        command.add("-serial");
        command.add("null");
        command.add("-serial");
        command.add("null");
        command.add("-kernel");
        command.add(microFlash.getAbsolutePath());
        command.add("-monitor");
        command.add("none");
        command.add("-display");
        command.add("none");
        command.add("-machine");
        command.add("pebble-snowy-bb");
        command.add("-cpu");
        command.add("cortex-m4");
        command.add("-drive");
        command.add("if=none,id=spi-flash,file=" + spiFlash.getAbsolutePath() + ",format=raw");
        command.add("-no-reboot");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(runtimeDirectory);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        builder.environment().put("PEBBLE_FB_PATH", framebuffer.getAbsolutePath());

        process = builder.start();
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized Integer exitCode() {
        if (process == null || process.isAlive()) {
            return null;
        }
        return process.exitValue();
    }

    public synchronized void stop() {
        Process current = process;
        process = null;
        if (current == null) {
            return;
        }
        current.destroy();
        try {
            if (!current.waitFor(1200, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly();
                current.waitFor(1200, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    public boolean waitForFirstFrame(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (hasValidFrame()) {
                return true;
            }
            Integer code = exitCode();
            if (code != null) {
                return false;
            }
            Thread.sleep(80);
        }
        return hasValidFrame();
    }

    public boolean readFrame(int[] argbPixels) {
        if (argbPixels == null || argbPixels.length < FRAME_BYTES || !framebuffer.isFile()) {
            return false;
        }

        ByteBuffer data = ByteBuffer.allocate(HEADER_BYTES + FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try (RandomAccessFile file = new RandomAccessFile(framebuffer, "r");
             FileChannel channel = file.getChannel()) {
            if (channel.size() < HEADER_BYTES + FRAME_BYTES) {
                return false;
            }
            int total = 0;
            while (data.hasRemaining()) {
                int read = channel.read(data, total);
                if (read <= 0) {
                    return false;
                }
                total += read;
            }
        } catch (IOException ignored) {
            return false;
        }

        data.flip();
        int magic = data.getInt();
        int version = data.getInt();
        int width = data.getInt();
        int height = data.getInt();
        int stride = data.getInt();
        int format = data.getInt();
        int sequence = data.getInt();
        data.position(HEADER_BYTES);
        if (magic != MAGIC || version != 1 || width != WIDTH || height != HEIGHT
                || stride != WIDTH || format != 1 || sequence <= 0) {
            return false;
        }

        for (int index = 0; index < FRAME_BYTES; index++) {
            int pebblePixel = data.get() & 0xff;
            int red = ((pebblePixel >> 6) & 0x03) * 85;
            int green = ((pebblePixel >> 4) & 0x03) * 85;
            int blue = ((pebblePixel >> 2) & 0x03) * 85;
            argbPixels[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        return true;
    }

    public String readLogTail(int maxBytes) {
        if (!logFile.isFile()) {
            return "QEMU produced no log file.";
        }
        int limit = Math.max(1024, maxBytes);
        try (RandomAccessFile file = new RandomAccessFile(logFile, "r")) {
            long start = Math.max(0, file.length() - limit);
            file.seek(start);
            byte[] bytes = new byte[(int) (file.length() - start)];
            file.readFully(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            return "Cannot read QEMU log: " + error.getMessage();
        }
    }

    public File getFramebufferFile() {
        return framebuffer;
    }

    private void prepareFiles() throws IOException {
        if (!runtimeDirectory.isDirectory() && !runtimeDirectory.mkdirs()) {
            throw new IOException("Cannot create runtime directory: " + runtimeDirectory);
        }
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
        copyAsset(ASSET_ROOT + "qemu_spi_flash.bin", spiFlash, false);
        if (framebuffer.exists() && !framebuffer.delete()) {
            throw new IOException("Cannot reset framebuffer: " + framebuffer);
        }
        if (logFile.exists() && !logFile.delete()) {
            throw new IOException("Cannot reset QEMU log: " + logFile);
        }
    }

    private void copyAsset(String assetPath, File destination, boolean overwrite) throws IOException {
        if (!overwrite && destination.isFile() && destination.length() > 0) {
            return;
        }
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream input = new BufferedInputStream(context.getAssets().open(assetPath));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Cannot replace " + destination);
        }
        if (!temporary.renameTo(destination)) {
            copyFile(temporary, destination);
            if (!temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private boolean hasValidFrame() {
        int[] scratch = new int[FRAME_BYTES];
        return readFrame(scratch);
    }
}
