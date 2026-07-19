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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
    private static final int MAGIC = 0x50424642; // PBFB
    private static final int MAX_CONSOLE_CHARS = 12 * 1024;
    private static final String ASSET_ROOT = "pebble/basalt/";

    private final Context context;
    private final File runtimeDirectory;
    private final File microFlash;
    private final File spiFlash;
    private final File framebuffer;
    private final File logFile;
    private final Object consoleLock = new Object();
    private final StringBuilder consoleTail = new StringBuilder();
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
    private Socket consoleSocket;
    private Thread consoleThread;
    private int protocolPort;
    private int consolePort;
    private volatile boolean firmwareReady;

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
        protocolPort = chooseUnusedPort();
        consolePort = chooseUnusedPort();
        firmwareReady = false;
        synchronized (consoleLock) {
            consoleTail.setLength(0);
        }

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

        List<String> command = new ArrayList<>();
        command.add(qemu.getAbsolutePath());
        command.add("-rtc");
        command.add("base=localtime");
        command.add("-serial");
        command.add("null");
        command.add("-serial");
        command.add("tcp::" + protocolPort + ",server=on,wait=off,nodelay=on");
        command.add("-serial");
        command.add("tcp::" + consolePort + ",server=on,wait=off,nodelay=on");
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
        command.add("if=none,id=spi-flash,file="
                + spiFlash.getAbsolutePath()
                + ",format=raw");
        command.add("-no-reboot");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(runtimeDirectory);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        builder.environment().put("PEBBLE_FB_PATH", framebuffer.getAbsolutePath());

        process = builder.start();
        startConsoleCapture();
    }

    /**
     * Waits for the real Pebble phone protocol handshake.
     *
     * The diagnostic serial stream is binary/mixed on some Android QEMU builds, so text such as
     * "Ready for communication" is deliberately not used as a readiness signal.
     */
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
            StringBuilder message = new StringBuilder();
            if (exitCode == null) {
                message.append("PebbleOS phone protocol did not become ready");
            } else {
                message.append("PebbleOS stopped with code ").append(exitCode);
            }

            String diagnostics = consoleDiagnostics();
            if (!diagnostics.isEmpty()) {
                message.append("\n\nConsole diagnostics:\n").append(diagnostics);
            }
            String qemuLog = readLogTail(4 * 1024);
            if (!qemuLog.isEmpty() && !"QEMU produced no log file.".equals(qemuLog)) {
                message.append("\n\nQEMU log:\n").append(qemuLog);
            }
            throw new IOException(message.toString(), error);
        }
    }

    /**
     * Installs a PBW only when its UUID/SHA fingerprint is not already present in persistent SPI
     * flash; otherwise only AppRunState is sent.
     *
     * @return true when bytes were installed, false when an existing app was launched directly.
     */
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
            PebbleAppInstaller installer = new PebbleAppInstaller(link, progressListener);
            if (registry.isInstalled(header.getUuid(), fingerprint)) {
                installer.launch(header.getUuid(), header.getAppName());
                return false;
            }
            installer.install(bundle);
            registry.markInstalled(header.getUuid(), fingerprint);
            return true;
        }
    }

    /** Compatibility wrapper retained for older probe code. */
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

        closeConsoleCapture();
        closeFramebufferReader();

        Process current = process;
        process = null;
        if (current == null) {
            return;
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

    /** Returns the current QEMU framebuffer generation without copying pixel data. */
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

    public String readLogTail(int maxBytes) {
        if (!logFile.isFile()) {
            return "QEMU produced no log file.";
        }
        int limit = Math.max(1_024, maxBytes);
        try (RandomAccessFile file = new RandomAccessFile(logFile, "r")) {
            long start = Math.max(0, file.length() - limit);
            file.seek(start);
            byte[] bytes = new byte[(int) (file.length() - start)];
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            return "Cannot read QEMU log: " + error.getMessage();
        }
    }

    public File getFramebufferFile() {
        return framebuffer;
    }

    private PebbleProtocolLink protocolLink(long timeoutMillis)
            throws IOException, InterruptedException {
        synchronized (this) {
            if (protocolLink != null) {
                return protocolLink;
            }
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
        if (logFile.exists() && !logFile.delete()) {
            throw new IOException("Cannot reset QEMU log: " + logFile);
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

    private void startConsoleCapture() {
        Thread thread = new Thread(this::captureConsole, "PebbleQemuConsole");
        thread.setDaemon(true);
        consoleThread = thread;
        thread.start();
    }

    private void captureConsole() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        Socket connected = null;
        try {
            while (!Thread.currentThread().isInterrupted()
                    && System.nanoTime() < deadline
                    && connected == null) {
                if (!isRunning()) {
                    return;
                }
                Socket candidate = new Socket();
                try {
                    candidate.connect(
                            new InetSocketAddress("127.0.0.1", consolePort),
                            600
                    );
                    candidate.setSoTimeout(1_000);
                    candidate.setTcpNoDelay(true);
                    connected = candidate;
                } catch (IOException error) {
                    try {
                        candidate.close();
                    } catch (IOException ignored) {
                    }
                    Thread.sleep(100);
                }
            }
            if (connected == null) {
                appendConsoleMessage("[diagnostic console unavailable]\n");
                return;
            }

            synchronized (this) {
                if (!isRunning()) {
                    connected.close();
                    return;
                }
                consoleSocket = connected;
            }

            byte[] buffer = new byte[4_096];
            try (InputStream input = connected.getInputStream()) {
                while (!Thread.currentThread().isInterrupted() && isRunning()) {
                    try {
                        int read = input.read(buffer);
                        if (read < 0) {
                            break;
                        }
                        if (read > 0) {
                            appendConsoleBytes(buffer, read);
                        }
                    } catch (SocketTimeoutException ignored) {
                        // Periodically re-check process and interruption state.
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException error) {
            if (isRunning()) {
                appendConsoleMessage("\n[diagnostic console closed: "
                        + safeMessage(error)
                        + "]\n");
            }
        } finally {
            synchronized (this) {
                if (consoleSocket == connected) {
                    consoleSocket = null;
                }
            }
            if (connected != null) {
                try {
                    connected.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private synchronized void closeConsoleCapture() {
        Socket currentSocket = consoleSocket;
        consoleSocket = null;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
            }
        }

        Thread currentThread = consoleThread;
        consoleThread = null;
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    private void appendConsoleBytes(byte[] bytes, int length) {
        StringBuilder printable = new StringBuilder(length);
        boolean inBinaryRun = false;
        for (int index = 0; index < length; index++) {
            int value = bytes[index] & 0xff;
            boolean visible = value == '\n'
                    || value == '\r'
                    || value == '\t'
                    || (value >= 0x20 && value <= 0x7e);
            if (visible) {
                printable.append((char) value);
                inBinaryRun = false;
            } else if (!inBinaryRun) {
                printable.append("[binary]");
                inBinaryRun = true;
            }
        }
        appendConsoleMessage(printable.toString());
    }

    private void appendConsoleMessage(String value) {
        synchronized (consoleLock) {
            consoleTail.append(value);
            if (consoleTail.length() > MAX_CONSOLE_CHARS) {
                consoleTail.delete(0, consoleTail.length() - MAX_CONSOLE_CHARS);
            }
        }
    }

    private String consoleDiagnostics() {
        synchronized (consoleLock) {
            return consoleTail.toString().trim();
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

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private boolean hasValidFrame() {
        int[] scratch = new int[FRAME_BYTES];
        return readFrame(scratch);
    }
}
