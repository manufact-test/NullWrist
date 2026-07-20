package com.manufacttest.pebblereardisplay.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Installs and launches modern Pebble apps using BlobDB, AppFetch and PutBytes. */
public final class PebbleAppInstaller {
    private static final int ENDPOINT_APP_RUN_STATE = 0x0034;
    private static final int ENDPOINT_APP_FETCH = 0x1771;
    private static final int ENDPOINT_BLOB_DB = 0xB1DB;
    private static final int ENDPOINT_PUT_BYTES = 0xBEEF;

    private static final int APP_RUN_STATE_RUNNING = 0x01;
    private static final int APP_RUN_STATE_RUN_COMMAND = 0x01;
    private static final int APP_RUN_STATE_STATUS_COMMAND = 0x03;
    private static final int BLOB_DATABASE_APP = 2;
    private static final int BLOB_STATUS_SUCCESS = 1;
    private static final int BLOB_STATUS_TRY_LATER = 0x0B;
    private static final int PUT_BYTES_ACK = 1;
    private static final int PUT_BYTES_NACK = 2;

    private static final int PART_RESOURCES = 4;
    private static final int PART_BINARY = 5;
    private static final int PART_WORKER = 7;
    private static final int APP_INSTALL_FLAG = 0x80;
    private static final int TRANSFER_CHUNK_BYTES = 1000;
    private static final int PUT_BYTES_BUSY_RETRIES = 10;
    private static final long TRANSFER_PACING_MILLIS = 12L;
    private static final long PART_SETTLE_MILLIS = 40L;
    private static final long PUT_BYTES_TIMEOUT_MILLIS = 30_000L;
    private static final long RUN_CONFIRM_TIMEOUT_MILLIS = 15_000L;
    private static final long INSTALL_CONFIRM_TIMEOUT_MILLIS = 30_000L;
    private static final long APP_FETCH_SETTLE_MILLIS = 250L;
    private static final long RUN_STATUS_POLL_MILLIS = 2_000L;
    private static final long RUN_RETRY_MILLIS = 5_000L;

    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(0x4100);

    private final PebbleProtocolLink link;
    private final ProgressListener progressListener;
    private int totalSent;
    private int totalSize;
    private int lastPublishedPercent = -1;
    private long lastPublishedNanos;

    public PebbleAppInstaller(PebbleProtocolLink link, ProgressListener progressListener) {
        this.link = link;
        this.progressListener = progressListener;
    }

    public void install(PebblePbwBundle bundle) throws IOException, InterruptedException {
        PebblePbwBundle.AppHeader header = bundle.getHeader();
        totalSent = 0;
        totalSize = bundle.getTotalTransferBytes();
        publish("Preparing " + header.getAppName());

        insertAppMetadata(header);
        int installId = requestAppStart(header.getUuid());

        // PutBytes is strictly request/response ordered. Drain only once at the beginning of a
        // fresh AppFetch transaction. Clearing before every packet can delete a delayed busy NACK
        // or ACK and leaves the phone waiting forever while Android waits for a response it erased.
        link.clearEndpoint(ENDPOINT_PUT_BYTES);

        sendPart(PART_BINARY, bundle.getApplication(), installId, "application");
        if (bundle.getResources() != null) {
            sendPart(PART_RESOURCES, bundle.getResources(), installId, "resources");
        }
        if (bundle.getWorker() != null) {
            sendPart(PART_WORKER, bundle.getWorker(), installId, "worker");
        }
        Thread.sleep(APP_FETCH_SETTLE_MILLIS);
        publish("Launching " + header.getAppName());
        awaitRunning(header.getUuid(), header.getAppName(), INSTALL_CONFIRM_TIMEOUT_MILLIS);
    }

    /** Launches an app already present in Pebble AppDB/SPI flash and waits for its UUID. */
    public void launch(UUID uuid, String appName) throws IOException, InterruptedException {
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);
        sendRunCommand(uuid);
        awaitRunning(uuid, appName, RUN_CONFIRM_TIMEOUT_MILLIS);
    }

    private void insertAppMetadata(PebblePbwBundle.AppHeader header)
            throws IOException, InterruptedException {
        byte[] uuid = uuidBytes(header.getUuid());
        byte[] metadata = appMetadata(header, uuid);

        for (int attempt = 0; attempt < 8; attempt++) {
            int token = nextToken();
            ByteBuffer payload = ByteBuffer.allocate(1 + 2 + 1 + 1 + 16 + 2 + metadata.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            payload.put((byte) 0x01);
            payload.putShort((short) token);
            payload.put((byte) BLOB_DATABASE_APP);
            payload.put((byte) uuid.length);
            payload.put(uuid);
            payload.putShort((short) metadata.length);
            payload.put(metadata);
            link.clearEndpoint(ENDPOINT_BLOB_DB);
            link.sendPebblePacket(ENDPOINT_BLOB_DB, payload.array());

            byte[] response = link.awaitEndpoint(
                    ENDPOINT_BLOB_DB,
                    value -> value.length >= 3 && unsignedShortLe(value, 0) == token,
                    8_000
            );
            int status = response[2] & 0xff;
            if (status == BLOB_STATUS_SUCCESS) {
                return;
            }
            if (status == BLOB_STATUS_TRY_LATER) {
                Thread.sleep(250L * (attempt + 1));
                continue;
            }
            throw new IOException("Pebble AppDB rejected metadata with status " + status);
        }
        throw new IOException("Pebble AppDB remained busy");
    }

    private int requestAppStart(UUID uuid) throws IOException, InterruptedException {
        byte[] rawUuid = uuidBytes(uuid);
        link.clearEndpoint(ENDPOINT_APP_FETCH);
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);

        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) APP_RUN_STATE_RUN_COMMAND);
        start.put(rawUuid);
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());

        byte[] fetch = link.awaitEndpoint(
                ENDPOINT_APP_FETCH,
                value -> value.length >= 21
                        && (value[0] & 0xff) == 0x01
                        && Arrays.equals(Arrays.copyOfRange(value, 1, 17), rawUuid),
                15_000
        );
        return ByteBuffer.wrap(fetch, 17, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void sendPart(int partType, byte[] object, int installId, String partName)
            throws IOException, InterruptedException {
        ByteBuffer init = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        init.put((byte) 0x01);
        init.putInt(object.length);
        init.put((byte) (partType | APP_INSTALL_FLAG));
        init.putInt(installId);
        int cookie = sendPutBytes(init.array(), null, partName + " init").cookie;

        int chunkCount = (object.length + TRANSFER_CHUNK_BYTES - 1) / TRANSFER_CHUNK_BYTES;
        int chunkIndex = 0;
        for (int offset = 0; offset < object.length; offset += TRANSFER_CHUNK_BYTES) {
            int length = Math.min(TRANSFER_CHUNK_BYTES, object.length - offset);
            ByteBuffer put = ByteBuffer.allocate(9 + length).order(ByteOrder.BIG_ENDIAN);
            put.put((byte) 0x02);
            put.putInt(cookie);
            put.putInt(length);
            put.put(object, offset, length);
            chunkIndex++;
            sendPutBytes(
                    put.array(),
                    cookie,
                    partName + " chunk " + chunkIndex + "/" + chunkCount
            );
            totalSent += length;
            publishProgress();
            Thread.sleep(TRANSFER_PACING_MILLIS);
        }

        ByteBuffer commit = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
        commit.put((byte) 0x03);
        commit.putInt(cookie);
        commit.putInt(stm32Crc32(object));
        sendPutBytes(commit.array(), cookie, partName + " commit");

        ByteBuffer install = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        install.put((byte) 0x05);
        install.putInt(cookie);
        // Commit cleanup resets PebbleOS' active PutBytes token before the separate Install
        // command is handled. A successful Install therefore responds with ACK token 0.
        sendPutBytes(install.array(), 0, partName + " install");
        Thread.sleep(PART_SETTLE_MILLIS);
    }

    private PutBytesResponse sendPutBytes(byte[] payload, Integer expectedCookie, String stage)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt <= PUT_BYTES_BUSY_RETRIES; attempt++) {
            link.sendPebblePacket(ENDPOINT_PUT_BYTES, payload);
            byte[] response = awaitPutBytesResponse(expectedCookie, stage);
            int result = response[0] & 0xff;
            int cookie = putBytesCookie(response);

            if (result == PUT_BYTES_ACK) {
                return new PutBytesResponse(cookie);
            }

            // PebbleOS sends a token-zero NACK when the PutBytes receiver is temporarily busy or
            // could not take its short internal semaphore. The request was not accepted, so it is
            // safe to retry it after a small backoff. 0.8.5/0.8.7 rejected this response by cookie
            // and waited for 30 seconds, which is the stalled progress bar seen on Titan 2.
            if (result == PUT_BYTES_NACK && cookie == 0 && attempt < PUT_BYTES_BUSY_RETRIES) {
                Thread.sleep(Math.min(250L, 20L * (attempt + 1)));
                continue;
            }

            throw new IOException(
                    "Pebble rejected " + stage + " (response=" + result
                            + ", token=" + Integer.toUnsignedString(cookie) + ")"
            );
        }
        throw new IOException("Pebble remained busy during " + stage);
    }

    private byte[] awaitPutBytesResponse(Integer expectedCookie, String stage)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(PUT_BYTES_TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(
                    1L,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            );
            byte[] response;
            try {
                response = link.awaitEndpoint(
                        ENDPOINT_PUT_BYTES,
                        value -> value != null && value.length >= 5,
                        remaining
                );
            } catch (IOException error) {
                if (isEndpointTimeout(error)) {
                    throw new IOException("Timed out during " + stage + " (" + link.diagnostics() + ")", error);
                }
                throw error;
            }

            int result = response[0] & 0xff;
            int cookie = putBytesCookie(response);
            if (isPutBytesResponseFor(response, expectedCookie)) {
                return response;
            }

            // A response from an older completed transaction can still be queued after an app
            // switch. Consume it and keep waiting. A token-zero NACK is never stale: it describes
            // the request just sent and must be handled as a retryable busy response.
            if (result == PUT_BYTES_NACK && cookie == 0) {
                return response;
            }
        }
        throw new IOException("Timed out during " + stage + " (" + link.diagnostics() + ")");
    }

    private void awaitRunning(UUID uuid, String appName, long timeoutMillis)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long nextRunRetry = started + TimeUnit.MILLISECONDS.toNanos(RUN_RETRY_MILLIS);

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

    static boolean isRunningStateFor(byte[] value, UUID expectedUuid) {
        if (value == null || value.length < 17 || (value[0] & 0xff) != APP_RUN_STATE_RUNNING) {
            return false;
        }
        return Arrays.equals(
                Arrays.copyOfRange(value, 1, 17),
                uuidBytes(expectedUuid)
        );
    }

    static boolean isPutBytesResponseFor(byte[] value, Integer expectedCookie) {
        if (value == null || value.length < 5) {
            return false;
        }
        int result = value[0] & 0xff;
        int cookie = putBytesCookie(value);
        return (result == PUT_BYTES_NACK && cookie == 0)
                || expectedCookie == null
                || cookie == expectedCookie;
    }

    static int putBytesCookie(byte[] response) {
        return ByteBuffer.wrap(response, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static boolean isEndpointTimeout(IOException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("Timed out waiting for Pebble endpoint");
    }

    private void publishProgress() {
        if (progressListener == null) {
            return;
        }
        int percent = totalSize <= 0 ? 0 : Math.min(100, Math.round(totalSent * 100f / totalSize));
        long now = System.nanoTime();
        if (percent < 100
                && percent - lastPublishedPercent < 2
                && now - lastPublishedNanos < TimeUnit.MILLISECONDS.toNanos(200)) {
            return;
        }
        lastPublishedPercent = percent;
        lastPublishedNanos = now;
        progressListener.onProgress("Installing watchface… " + percent + "%", totalSent, totalSize);
    }

    private void publish(String message) {
        if (progressListener != null) {
            progressListener.onProgress(message, totalSent, totalSize);
        }
    }

    private static byte[] appMetadata(PebblePbwBundle.AppHeader header, byte[] uuid) {
        ByteBuffer metadata = ByteBuffer.allocate(126).order(ByteOrder.LITTLE_ENDIAN);
        metadata.put(uuid);
        metadata.putInt(header.getFlags());
        metadata.putInt(header.getIconResourceId());
        metadata.put((byte) header.getAppVersionMajor());
        metadata.put((byte) header.getAppVersionMinor());
        metadata.put((byte) header.getSdkVersionMajor());
        metadata.put((byte) header.getSdkVersionMinor());
        metadata.put((byte) 0);
        metadata.put((byte) 0);

        byte[] name = header.getAppName().getBytes(StandardCharsets.UTF_8);
        metadata.put(name, 0, Math.min(name.length, 96));
        while (metadata.position() < metadata.capacity()) {
            metadata.put((byte) 0);
        }
        return metadata.array();
    }

    static int stm32Crc32(byte[] data) {
        int crc = 0xffffffff;
        for (int offset = 0; offset < data.length; offset += 4) {
            int remaining = Math.min(4, data.length - offset);
            int word;
            if (remaining == 4) {
                word = (data[offset] & 0xff)
                        | ((data[offset + 1] & 0xff) << 8)
                        | ((data[offset + 2] & 0xff) << 16)
                        | ((data[offset + 3] & 0xff) << 24);
            } else {
                word = 0;
                for (int index = 0; index < remaining; index++) {
                    word = (word << 8) | (data[offset + index] & 0xff);
                }
            }
            crc ^= word;
            for (int bit = 0; bit < 32; bit++) {
                crc = (crc & 0x80000000) != 0
                        ? (crc << 1) ^ 0x04C11DB7
                        : crc << 1;
            }
        }
        return crc;
    }

    private static byte[] uuidBytes(UUID uuid) {
        ByteBuffer value = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        value.putLong(uuid.getMostSignificantBits());
        value.putLong(uuid.getLeastSignificantBits());
        return value.array();
    }

    private static int nextToken() {
        int token = NEXT_TOKEN.updateAndGet(value -> value >= 0xfffe ? 1 : value + 1);
        return token & 0xffff;
    }

    private static int unsignedShortLe(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    public interface ProgressListener {
        void onProgress(String message, int sentBytes, int totalBytes);
    }

    private static final class PutBytesResponse {
        final int cookie;

        PutBytesResponse(int cookie) {
            this.cookie = cookie;
        }
    }
}
