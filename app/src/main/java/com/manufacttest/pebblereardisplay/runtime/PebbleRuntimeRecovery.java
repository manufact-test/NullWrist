package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Recovery operations that must survive OEM /proc restrictions and Android process death. */
final class PebbleRuntimeRecovery {
    private static final String ASSET_SPI_FLASH = "pebble/basalt/qemu_spi_flash.bin";
    private static final long TERM_WAIT_MILLIS = 900L;
    private static final long KILL_WAIT_MILLIS = 500L;
    private static final long POLL_MILLIS = 25L;

    private PebbleRuntimeRecovery() {
    }

    /** Ensures a QEMU orphan from a killed Android process cannot overlap a new runtime. */
    static void prepareBeforeStart(Context context) throws IOException {
        File runtimeDirectory = runtimeDirectory(context);
        terminateStaleQemuProcess(new File(runtimeDirectory, "qemu.pid"));
    }

    /**
     * Rebuilds only PebbleOS-owned SPI/AppDB state from the bundled known-good image.
     * Imported PBW files live outside this runtime directory and are deliberately preserved.
     */
    static void resetPersistentState(Context context) throws IOException {
        File runtimeDirectory = runtimeDirectory(context);
        if (!runtimeDirectory.isDirectory() && !runtimeDirectory.mkdirs()) {
            throw new IOException("Cannot create Pebble runtime directory: " + runtimeDirectory);
        }

        File pidFile = new File(runtimeDirectory, "qemu.pid");
        terminateStaleQemuProcess(pidFile);

        File spiFlash = new File(runtimeDirectory, "qemu_spi_flash.bin");
        copyAssetAtomically(context, ASSET_SPI_FLASH, spiFlash);
        new InstalledWatchfaceRegistry(context).clear();

        deleteIfPresent(new File(runtimeDirectory, "framebuffer.bin"), "Pebble framebuffer");
        deleteIfPresent(new File(runtimeDirectory, "frame-events.fifo"), "framebuffer event pipe");
        deleteIfPresent(pidFile, "QEMU PID file");
    }

    private static File runtimeDirectory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "pebble-runtime/basalt");
    }

    private static void terminateStaleQemuProcess(File pidFile) throws IOException {
        if (!pidFile.isFile()) {
            return;
        }

        int pid = readPid(pidFile);
        if (pid <= 0 || pid == android.os.Process.myPid()) {
            return;
        }

        ProcessIdentity identity = inspectProcessIdentity(pid);
        if (identity == ProcessIdentity.DIFFERENT_PROCESS) {
            return;
        }

        // Some OEM Android builds hide /proc/<pid>/cmdline. qemu.pid is app-private and
        // kill(pid, 0) additionally proves the caller may signal this PID. For a real orphaned
        // QEMU child this succeeds even when /proc inspection is blocked.
        if (!canSignal(pid)) {
            return;
        }
        if (!sendSignal(pid, OsConstants.SIGTERM)) {
            return;
        }
        if (waitForExit(pid, TERM_WAIT_MILLIS)) {
            return;
        }

        ProcessIdentity beforeKill = inspectProcessIdentity(pid);
        if (beforeKill == ProcessIdentity.DIFFERENT_PROCESS) {
            return;
        }
        if (!sendSignal(pid, OsConstants.SIGKILL)) {
            return;
        }
        if (!waitForExit(pid, KILL_WAIT_MILLIS)) {
            throw new IOException(
                    "Refusing to start a second QEMU while stale process " + pid + " is alive"
            );
        }
    }

    private static int readPid(File pidFile) {
        try {
            String value = new String(
                    Files.readAllBytes(pidFile.toPath()),
                    StandardCharsets.US_ASCII
            ).trim();
            return Integer.parseInt(value);
        } catch (IOException | NumberFormatException ignored) {
            return -1;
        }
    }

    private static ProcessIdentity inspectProcessIdentity(int pid) {
        try {
            String command = new String(
                    Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath()),
                    StandardCharsets.UTF_8
            );
            if (command.isBlank()) {
                return ProcessIdentity.UNKNOWN;
            }
            return command.contains("libpebble_qemu_exec.so")
                            || command.contains("pebble-qemu-launcher")
                    ? ProcessIdentity.PEBBLE_QEMU
                    : ProcessIdentity.DIFFERENT_PROCESS;
        } catch (IOException ignored) {
            return ProcessIdentity.UNKNOWN;
        }
    }

    private static boolean canSignal(int pid) throws IOException {
        try {
            Os.kill(pid, 0);
            return true;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ESRCH || error.errno == OsConstants.EPERM) {
                return false;
            }
            throw new IOException("Could not inspect stale PebbleOS process " + pid, error);
        }
    }

    private static boolean sendSignal(int pid, int signal) throws IOException {
        try {
            Os.kill(pid, signal);
            return true;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ESRCH || error.errno == OsConstants.EPERM) {
                return false;
            }
            throw new IOException("Could not stop stale PebbleOS process " + pid, error);
        }
    }

    private static boolean waitForExit(int pid, long timeoutMillis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (!canSignal(pid)) {
                return true;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping stale PebbleOS process", interrupted);
            }
        }
        return !canSignal(pid);
    }

    private static void copyAssetAtomically(Context context, String assetPath, File destination)
            throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".recovery.tmp");
        try (InputStream input = new BufferedInputStream(context.getAssets().open(assetPath));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }

        if (destination.exists() && !destination.delete()) {
            throw new IOException("Cannot replace PebbleOS SPI flash");
        }
        if (!temporary.renameTo(destination)) {
            try (InputStream input = Files.newInputStream(temporary.toPath());
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            if (!temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static void deleteIfPresent(File file, String label) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot reset " + label);
        }
    }

    private enum ProcessIdentity {
        PEBBLE_QEMU,
        DIFFERENT_PROCESS,
        UNKNOWN
    }
}
