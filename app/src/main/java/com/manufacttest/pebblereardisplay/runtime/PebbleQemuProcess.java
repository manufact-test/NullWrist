package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Owns one native Pebble Time QEMU process and its persistent runtime files. */
public final class PebbleQemuProcess {
    public static final int WIDTH = 144;
    public static final int HEIGHT = 168;

    private static final int HEADER_BYTES = 64;
    private static final int FRAME_BYTES = WIDTH * HEIGHT;
    private static final int SEQUENCE_OFFSET_BYTES = 24;
    private static final int MAGIC = 0x50424642;
    private static final String ASSET_ROOT = "pebble/basalt/";

    private final Context context;
    private final File runtimeDirectory;
    private final File microFlash;
    private final File spiFlash;
    private final File framebuffer;
    private final File frameEventPipe;
    private final File pidFile;
    private final ByteBuffer frameData = ByteBuffer
            .allocate(HEADER_BYTES + FRAME_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer sequenceData = ByteBuffer
            .allocate(Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);

    private Process process;
    private PebbleProtocolLink protocolLink;
    private RandomAccessFile framebufferReader;
    private FileChannel framebufferChannel;
    private int protocolPort;
    private int qemuPid = -1;
    private volatile boolean firmwareReady;
    private volatile boolean paused;

    public PebbleQemuProcess(Context context) {
        this.context = context.getApplicationContext();
        runtimeDirectory = new File(this.context.getFilesDir(), "pebble-runtime/basalt");
        microFlash = new File(runtimeDirectory, "qemu_micro_flash.bin");
        spiFlash = new File(runtimeDirectory, "qemu_spi_flash.bin");
        framebuffer = new File(runtimeDirectory, "framebuffer.bin");
        frameEventPipe = new File(runtimeDirectory, "frame-events.fifo");
        pidFile = new File(runtimeDirectory, "qemu.pid");
    }

    public synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }

        prepareFiles();
        protocolPort = chooseUnusedPort();
        firmwareReady = false;
        paused = false;
        qemuPid = -1;

        File qemu = new File(
                context.getApplicationInfo().nativeLibraryDir,
                "libpebble_qemu_exec.so"
        );
        if (!qemu.isFile()) {
            throw new IOException("Bundled QEMU binary is missing: " + qemu);
        }
        if (!qemu.canExecute()) {
            throw new IOException("Bundled QEMU binary is not executable: " + qemu);
        }

        List<String> qemuCommand = new ArrayList<>();
        qemuCommand.add(qemu.getAbsolutePath());
        qemuCommand.add("-rtc");
        qemuCommand.add("base=localtime");
        qemuCommand.add("-serial");
        qemuCommand.add("null");
        qemuCommand.add("-serial");
        qemuCommand.add("tcp::" + protocolPort + ",server=on,wait=off,nodelay=on");
        qemuCommand.add("-serial");
        qemuCommand.add("null");
        qemuCommand.add("-kernel");
        qemuCommand.add(microFlash.getAbsolutePath());
        qemuCommand.add("-monitor");
        qemuCommand.add("none");
        qemuCommand.add("-display");
        qemuCommand.add("none");
        qemuCommand.add("-machine");
        qemuCommand.add("pebble-snowy-bb");
        qemuCommand.add("-cpu");
        qemuCommand.add("cortex-m4");
        qemuCommand.add("-drive");
        qemuCommand.add("if=none,id=spi-flash,file="
                + spiFlash.getAbsolutePath()
                + ",format=raw");
        qemuCommand.add("-no-reboot");

        List<String> launcher = new ArrayList<>();
        launcher.add("/system/bin/sh");
        launcher.add("-c");
        launcher.add("echo $$ > \"$PEBBLE_QEMU_PID_PATH\"; exec \"$@\"");
        launcher.add("pebble-qemu-launcher");
        launcher.addAll(qemuCommand);

        ProcessBuilder builder = new ProcessBuilder(launcher);
        builder.directory(runtimeDirectory);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
        builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        builder.environment().put("PEBBLE_FB_PATH", framebuffer.getAbsolutePath());
        builder.environment().put("PEBBLE_FB_EVENT_PATH", frameEventPipe.getAbsolutePath());
        builder.environment().put("PEBBLE_QEMU_PID_PATH", pidFile.getAbsolutePath());

        process = builder.start();
        try {
            qemuPid = awaitPid(2_000L);
        } catch (IOException error) {
            process.destroyForcibly();
            process = null;
            throw error;
        }
    }

    public boolean waitForFirmwareReady(long timeoutMillis)
            throws IOException, InterruptedException {
        if (firmwareReady) {
            return true;
        }
        if (!isRunning()) {
            throw new IOException("QEMU stopped before PebbleOS became ready");
        }

        try {
            protocolLink(timeoutMillis);
            if (!isRunning()) {
                throw new IOException("QEMU stopped while PebbleOS was becoming ready");
            }
            firmwareReady = true;
            return true;
        } catch (IOException error) {
            Integer exitCode = exitCode();
            String message = exitCode == null
                    ? "PebbleOS phone protocol did not become ready"
                    : "PebbleOS stopped with code " + exitCode;
            throw new IOException(message, error);
        }
    }

    public boolean activateWatchface(
            File pbwFile,
            InstalledWatchfaceRegistry registry,
            PebbleAppInstaller.ProgressListener progressListener
    ) throws IOException, InterruptedException {
        if (pbwFile == null || !pbwFile.isFile()) {
            throw new IOException("Selected PBW file is missing");
        }
        if (!isRunning()) {
            throw new IOException("PebbleOS is not running");
        }
        if (!firmwareReady) {
            waitForFirmwareReady(30_000);
        }

        String fingerprint = InstalledWatchfaceRegistry.sha256(pbwFile);
        PebbleProtocolLink link = protocolLink(20_000);
        try (PebblePbwBundle bundle = new PebblePbwBundle(pbwFile)) {
            PebblePbwBundle.AppHeader header = bundle.getHeader();
            if (registry.isInstalled(header.getUuid(), fingerprint)) {
                try {
                    new PebbleAppInstaller(link, progressListener).launch(
                            header.getUuid(),
                            header.getAppName()
                    );
                } catch (IOException firstFailure) {
                    invalidateProtocolLink(link);
                    link = protocolLink(8_000);
                    new PebbleAppInstaller(link, progressListener).launch(
                            header.getUuid(),
                            header.getAppName()
                    );
                }
                return false;
            }
            new PebbleAppInstaller(link, progressListener).install(bundle);
            registry.markInstalled(header.getUuid(), fingerprint);
            return true;
        } catch (IOException error) {
            invalidateProtocolLink(link);
            throw error;
        }
    }

    public void installWatchface(
            File pbwFile,
            PebbleAppInstaller.ProgressListener progressListener
    ) throws IOException, InterruptedException {
        activateWatchface(
                pbwFile,
                new InstalledWatchfaceRegistry(context),
                progressListener
        );
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized boolean isPaused() {
        return paused && isRunning();
    }

    public synchronized boolean pause() throws IOException {
        if (!isRunning() || paused) {
            return isRunning();
        }
        signal(OsConstants.SIGSTOP, "freeze");
        paused = true;
        return true;
    }

    public synchronized boolean resume() throws IOException {
        if (!isRunning() || !paused) {
            return isRunning();
        }
        signal(OsConstants.SIGCONT, "resume");
        paused = false;
        return true;
    }

    public synchronized Integer exitCode() {
        if (process == null || process.isAlive()) {
            return null;
        }
        return process.exitValue();
    }

    public synchronized void stop() {
        firmwareReady = false;

        PebbleProtocolLink currentLink = protocolLink;
        protocolLink = null;
        if (currentLink != null) {
            currentLink.close();
        }

        closeFramebufferReader();

        Process current = process;
        process = null;
        if (current == null) {
            paused = false;
            qemuPid = -1;
            return;
        }

        if (paused && qemuPid > 0) {
            try {
                Os.kill(qemuPid, OsConstants.SIGCONT);
            } catch (ErrnoException ignored) {
            }
            paused = false;
        }

        current.destroy();
        try {
            if (!current.waitFor(1_200, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly();
                current.waitFor(1_200, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        } finally {
            qemuPid = -1;
            pidFile.delete();
        }
    }

    public boolean waitForFirstFrame(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (hasValidFrame()) {
                return true;
            }
            if (exitCode() != null) {
                return false;
            }
            Thread.sleep(80);
        }
        return hasValidFrame();
    }

    public synchronized int readFrameSequence() {
        if (!framebuffer.isFile()) {
            return -1;
        }
        try {
            ensureFramebufferReader();
            if (framebufferChannel.size() < HEADER_BYTES + FRAME_BYTES) {
                return -1;
            }
            sequenceData.clear();
            int total = 0;
            while (sequenceData.hasRemaining()) {
                int read = framebufferChannel.read(
                        sequenceData,
                        SEQUENCE_OFFSET_BYTES + total
                );
                if (read <= 0) {
                    return -1;
                }
                total += read;
            }
            sequenceData.flip();
            return sequenceData.getInt();
        } catch (IOException error) {
            closeFramebufferReader();
            return -1;
        }
    }

    public synchronized boolean readFrame(int[] argbPixels) {
        if (argbPixels == null
                || argbPixels.length < FRAME_BYTES
                || !framebuffer.isFile()) {
            return false;
        }

        try {
            ensureFramebufferReader();
            if (framebufferChannel.size() < HEADER_BYTES + FRAME_BYTES) {
                return false;
            }
            frameData.clear();
            int total = 0;
            while (frameData.hasRemaining()) {
                int read = framebufferChannel.read(frameData, total);
                if (read <= 0) {
                    return false;
                }
                total += read;
            }
        } catch (IOException error) {
            closeFramebufferReader();
            return false;
        }

        frameData.flip();
        int magic = frameData.getInt();
        int version = frameData.getInt();
        int width = frameData.getInt();
        int height = frameData.getInt();
        int stride = frameData.getInt();
        int format = frameData.getInt();
        int sequence = frameData.getInt();
        frameData.position(HEADER_BYTES);
        if (magic != MAGIC
                || version != 1
                || width != WIDTH
                || height != HEIGHT
                || stride != WIDTH
                || format != 1
                || sequence <= 0) {
            return false;
        }

        for (int index = 0; index < FRAME_BYTES; index++) {
            int pebblePixel = frameData.get() & 0xff;
            int red = ((pebblePixel >> 6) & 0x03) * 85;
            int green = ((pebblePixel >> 4) & 0x03) * 85;
            int blue = ((pebblePixel >> 2) & 0x03) * 85;
            argbPixels[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        return true;
    }

    public File getFramebufferFile() {
        return framebuffer;
    }

    public File getFrameEventFile() {
        return frameEventPipe;
    }

    public String readLogTail(int maxBytes) {
        return "QEMU diagnostics are disabled in the battery-optimized runtime.";
    }

    private void signal(int signal, String operation) throws IOException {
        if (qemuPid <= 0) {
            throw new IOException("QEMU PID is unavailable");
        }
        try {
            Os.kill(qemuPid, signal);
        } catch (ErrnoException error) {
            throw new IOException("Could not " + operation + " PebbleOS", error);
        }
    }

    private int awaitPid(long timeoutMillis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (pidFile.isFile()) {
                try {
                    String value = new String(
                            Files.readAllBytes(pidFile.toPath()),
                            StandardCharsets.US_ASCII
                    ).trim();
                    int pid = Integer.parseInt(value);
                    if (pid > 0) {
                        return pid;
                    }
                } catch (IOException | NumberFormatException ignored) {
                }
            }
            if (process == null || !process.isAlive()) {
                break;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading QEMU PID", interrupted);
            }
        }
        throw new IOException("QEMU did not publish its process ID");
    }

    private PebbleProtocolLink protocolLink(long timeoutMillis)
            throws IOException, InterruptedException {
        PebbleProtocolLink stale = null;
        synchronized (this) {
            if (protocolLink != null && protocolLink.isHealthy()) {
                return protocolLink;
            }
            stale = protocolLink;
            protocolLink = null;
        }
        if (stale != null) {
            stale.close();
        }

        PebbleProtocolLink candidate = PebbleProtocolLink.connect(
                "127.0.0.1",
                protocolPort,
                timeoutMillis
        );
        synchronized (this) {
            if (!isRunning()) {
                candidate.close();
                throw new IOException("QEMU stopped before the protocol connection completed");
            }
            if (protocolLink == null) {
                protocolLink = candidate;
            } else {
                candidate.close();
            }
            return protocolLink;
        }
    }

    private void invalidateProtocolLink(PebbleProtocolLink expected) {
        synchronized (this) {
            if (protocolLink == expected) {
                protocolLink = null;
            }
        }
        if (expected != null) {
            expected.close();
        }
    }

    private void prepareFiles() throws IOException {
        if (!runtimeDirectory.isDirectory() && !runtimeDirectory.mkdirs()) {
            throw new IOException("Cannot create runtime directory: " + runtimeDirectory);
        }

        boolean existingSpiFlash = spiFlash.isFile() && spiFlash.length() > 0;
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
        copyAsset(ASSET_ROOT + "qemu_spi_flash.bin", spiFlash, false);
        if (!existingSpiFlash) {
            new InstalledWatchfaceRegistry(context).clear();
        }

        closeFramebufferReader();
        if (framebuffer.exists() && !framebuffer.delete()) {
            throw new IOException("Cannot reset framebuffer: " + framebuffer);
        }
        if (pidFile.exists() && !pidFile.delete()) {
            throw new IOException("Cannot reset QEMU PID file");
        }
        prepareFrameEventPipe();
    }

    private void prepareFrameEventPipe() throws IOException {
        if (frameEventPipe.exists() && !frameEventPipe.delete()) {
            throw new IOException("Cannot reset framebuffer event pipe");
        }
        try {
            Os.mkfifo(frameEventPipe.getAbsolutePath(), 0600);
        } catch (ErrnoException error) {
            throw new IOException("Cannot create framebuffer event pipe", error);
        }
    }

    private void copyAsset(String assetPath, File destination, boolean overwrite)
            throws IOException {
        if (!overwrite && destination.isFile() && destination.length() > 0) {
            return;
        }

        File temporary = new File(
                destination.getParentFile(),
                destination.getName() + ".tmp"
        );
        try (InputStream input = new BufferedInputStream(context.getAssets().open(assetPath));
             BufferedOutputStream output = new BufferedOutputStream(
                     new FileOutputStream(temporary)
             )) {
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

    private void ensureFramebufferReader() throws IOException {
        if (framebufferReader == null) {
            framebufferReader = new RandomAccessFile(framebuffer, "r");
            framebufferChannel = framebufferReader.getChannel();
        }
    }

    private void closeFramebufferReader() {
        if (framebufferChannel != null) {
            try {
                framebufferChannel.close();
            } catch (IOException ignored) {
            }
            framebufferChannel = null;
        }
        if (framebufferReader != null) {
            try {
                framebufferReader.close();
            } catch (IOException ignored) {
            }
            framebufferReader = null;
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

    private static int chooseUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private boolean hasValidFrame() {
        int[] scratch = new int[FRAME_BYTES];
        return readFrame(scratch);
    }
}
