package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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
    private final ByteBuffer frameData = ByteBuffer
            .allocate(HEADER_BYTES + FRAME_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);

    private Process process;
    private PebbleProtocolLink protocolLink;
    private RandomAccessFile framebufferReader;
    private FileChannel framebufferChannel;
    private int protocolPort;
    private int consolePort;
    private volatile boolean firmwareReady;
    private volatile String consoleTail = "";

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
        consoleTail = "";

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
        command.add("tcp::" + protocolPort + ",server=on,wait=off,nodelay=on");
        command.add("-serial");
        command.add("tcp::" + consolePort + ",server=on,wait=on,nodelay=on");
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

    public boolean waitForFirmwareReady(long timeoutMillis) throws IOException, InterruptedException {
        if (firmwareReady) {
            return true;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        IOException lastConnectionError = null;
        Socket console = null;

        while (System.nanoTime() < deadline && console == null) {
            if (!isRunning()) {
                throw new IOException("QEMU stopped before PebbleOS became ready");
            }
            Socket candidate = new Socket();
            try {
                candidate.connect(new InetSocketAddress("127.0.0.1", consolePort), 500);
                candidate.setSoTimeout(750);
                candidate.setTcpNoDelay(true);
                console = candidate;
            } catch (IOException error) {
                lastConnectionError = error;
                try {
                    candidate.close();
                } catch (IOException ignored) {
                }
                Thread.sleep(80);
            }
        }

        if (console == null) {
            throw new IOException("Could not connect to PebbleOS console", lastConnectionError);
        }

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (Socket socket = console; InputStream input = socket.getInputStream()) {
            while (System.nanoTime() < deadline) {
                if (!isRunning()) {
                    throw new IOException("QEMU stopped while PebbleOS was booting");
                }
                try {
                    int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }
                    received.write(buffer, 0, read);
                    trimConsoleBuffer(received, 64 * 1024);
                    String text = received.toString(StandardCharsets.UTF_8.name());
                    consoleTail = tail(text, 8 * 1024);
                    if (text.contains("Ready for communication")
                            || text.contains("<Launcher>")
                            || text.contains("<SDK Home>")) {
                        firmwareReady = true;
                        return true;
                    }
                } catch (SocketTimeoutException ignored) {
                    // Continue until the absolute deadline so slow first boots remain valid.
                }
            }
        }

        String detail = consoleTail.isEmpty() ? "" : "\n\nConsole:\n" + consoleTail;
        throw new IOException("PebbleOS did not report communication readiness" + detail);
    }

    /**
     * Installs a PBW only when its UUID/SHA fingerprint is not already present in persistent SPI flash,
     * otherwise it only sends the AppRunState launch command.
     *
     * @return true when bytes were installed, false when the existing app was launched directly.
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
        PebbleProtocolLink link = protocolLink();
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
        activateWatchface(pbwFile, new InstalledWatchfaceRegistry(context), progressListener);
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
        PebbleProtocolLink currentLink = protocolLink;
        protocolLink = null;
        if (currentLink != null) {
            currentLink.close();
        }
        closeFramebufferReader();

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

    public synchronized boolean readFrame(int[] argbPixels) {
        if (argbPixels == null || argbPixels.length < FRAME_BYTES || !framebuffer.isFile()) {
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
        if (magic != MAGIC || version != 1 || width != WIDTH || height != HEIGHT
                || stride != WIDTH || format != 1 || sequence <= 0) {
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
        int limit = Math.max(1024, maxBytes);
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

    private synchronized PebbleProtocolLink protocolLink() throws IOException, InterruptedException {
        if (protocolLink == null) {
            protocolLink = PebbleProtocolLink.connect("127.0.0.1", protocolPort, 20_000);
        }
        return protocolLink;
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

    private static void trimConsoleBuffer(ByteArrayOutputStream output, int maxBytes) throws IOException {
        if (output.size() <= maxBytes) {
            return;
        }
        byte[] bytes = output.toByteArray();
        output.reset();
        output.write(bytes, bytes.length - maxBytes, maxBytes);
    }

    private static String tail(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(value.length() - maxChars);
    }

    private boolean hasValidFrame() {
        int[] scratch = new int[FRAME_BYTES];
        return readFrame(scratch);
    }
}
