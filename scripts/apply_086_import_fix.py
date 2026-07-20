#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
installer_path = root / "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleAppInstaller.java"
build_path = root / "app/build.gradle.kts"
readme_path = root / "README.md"

text = installer_path.read_text(encoding="utf-8")
text = text.replace(
    "    private static final long RUN_CONFIRM_TIMEOUT_MILLIS = 12_000L;\n",
    "    private static final long RUN_CONFIRM_TIMEOUT_MILLIS = 15_000L;\n"
    "    private static final long INSTALL_CONFIRM_TIMEOUT_MILLIS = 30_000L;\n"
    "    private static final long APP_FETCH_SETTLE_MILLIS = 250L;\n"
    "    private static final long RUN_STATUS_POLL_MILLIS = 2_000L;\n"
    "    private static final long RUN_RETRY_MILLIS = 5_000L;\n"
)
text = text.replace(
    "        awaitRunning(header.getUuid(), header.getAppName(), RUN_CONFIRM_TIMEOUT_MILLIS);\n"
    "        publish(\"Launching \" + header.getAppName());\n",
    "        Thread.sleep(APP_FETCH_SETTLE_MILLIS);\n"
    "        publish(\"Launching \" + header.getAppName());\n"
    "        awaitRunning(header.getUuid(), header.getAppName(), INSTALL_CONFIRM_TIMEOUT_MILLIS);\n"
)
old_launch = '''    /** Launches an app already present in Pebble AppDB/SPI flash and waits for its UUID. */
    public void launch(UUID uuid, String appName) throws IOException, InterruptedException {
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);
        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) APP_RUN_STATE_RUN_COMMAND);
        start.put(uuidBytes(uuid));
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());
        awaitRunning(uuid, appName, RUN_CONFIRM_TIMEOUT_MILLIS);
    }
'''
new_launch = '''    /** Launches an app already present in Pebble AppDB/SPI flash and waits for its UUID. */
    public void launch(UUID uuid, String appName) throws IOException, InterruptedException {
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);
        sendRunCommand(uuid);
        awaitRunning(uuid, appName, RUN_CONFIRM_TIMEOUT_MILLIS);
    }
'''
if old_launch not in text:
    raise SystemExit("launch method marker not found")
text = text.replace(old_launch, new_launch)

pattern = re.compile(
    r"    private void awaitRunning\(UUID uuid, String appName, long timeoutMillis\)\n"
    r"            throws IOException, InterruptedException \{.*?\n"
    r"    \}\n\n"
    r"    static boolean isRunningStateFor",
    re.S,
)
replacement = '''    private void awaitRunning(UUID uuid, String appName, long timeoutMillis)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long nextRunRetry = started + TimeUnit.MILLISECONDS.toNanos(RUN_RETRY_MILLIS);

        // Do not clear this endpoint here. AppFetch completion and AppRunState callbacks are
        // asynchronous; clearing between short polls can delete the exact UUID confirmation.
        requestRunningStatus();
        while (System.nanoTime() < deadline) {
            long now = System.nanoTime();
            long remaining = Math.max(
                    1L,
                    TimeUnit.NANOSECONDS.toMillis(deadline - now)
            );
            try {
                link.awaitEndpoint(
                        ENDPOINT_APP_RUN_STATE,
                        value -> isRunningStateFor(value, uuid),
                        Math.min(RUN_STATUS_POLL_MILLIS, remaining)
                );
                return;
            } catch (IOException error) {
                if (!isEndpointTimeout(error)) {
                    throw error;
                }
            }

            now = System.nanoTime();
            if (now >= nextRunRetry) {
                // AppFetch normally launches the downloaded app itself. Re-send RUN only as a
                // recovery path after the fetch UI has had time to finish its transition.
                sendRunCommand(uuid);
                nextRunRetry = now + TimeUnit.MILLISECONDS.toNanos(RUN_RETRY_MILLIS);
            }
            requestRunningStatus();
        }
        throw new IOException("PebbleOS did not confirm " + appName + " as the running app");
    }

    private void sendRunCommand(UUID uuid) throws IOException {
        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) APP_RUN_STATE_RUN_COMMAND);
        start.put(uuidBytes(uuid));
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());
    }

    private void requestRunningStatus() throws IOException {
        ByteBuffer status = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        status.put((byte) APP_RUN_STATE_STATUS_COMMAND);
        status.put(new byte[16]);
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, status.array());
    }

    static boolean isRunningStateFor'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"awaitRunning replacement count={count}")
installer_path.write_text(text, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 19', 'versionCode = 20')
build = build.replace('versionName = "0.8.5"', 'versionName = "0.8.6"')
build_path.write_text(build, encoding="utf-8")

readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace(
    "Pebblehertz 0.8.5 repairs command ordering and thumbnail attribution exposed by the faster native-TCG runtime, and simplifies Night Mode setup.",
    "Pebblehertz 0.8.6 repairs imported-PBW completion after the stricter 0.8.5 AppRunState validation."
)
anchor = "### 0.8.5 command and thumbnail repair\n"
section = (
    "### 0.8.6 imported PBW completion repair\n\n"
    "AppFetch and AppRunState callbacks are asynchronous. Pebblehertz no longer clears the "
    "run-state response queue between short polls, waits up to 30 seconds for an imported app "
    "to finish, and retries the RUN command only after the fetch UI has had time to complete.\n\n"
)
if section not in readme:
    readme = readme.replace(anchor, section + anchor)
readme_path.write_text(readme, encoding="utf-8")

print("Applied Pebblehertz 0.8.6 import completion repair")
