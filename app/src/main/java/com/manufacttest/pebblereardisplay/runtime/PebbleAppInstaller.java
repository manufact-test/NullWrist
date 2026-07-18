package com.manufacttest.pebblereardisplay.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Installs one modern Pebble app using BlobDB, AppFetch and PutBytes. */
public final class PebbleAppInstaller {
    private static final int ENDPOINT_APP_RUN_STATE = 0x0034;
    private static final int ENDPOINT_APP_FETCH = 0x1771;
    private static final int ENDPOINT_BLOB_DB = 0xB1DB;
    private static final int ENDPOINT_PUT_BYTES = 0xBEEF;

    private static final int BLOB_DATABASE_APP = 2;
    private static final int BLOB_STATUS_SUCCESS = 1;
    private static final int BLOB_STATUS_TRY_LATER = 0x0B;
    private static final int PUT_BYTES_ACK = 1;

    private static final int PART_RESOURCES = 4;
    private static final int PART_BINARY = 5;
    private static final int PART_WORKER = 7;
    private static final int APP_INSTALL_FLAG = 0x80;
    private static final int TRANSFER_CHUNK_BYTES = 1000;

    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(0x4100);

    private final PebbleProtocolLink link;
    private final ProgressListener progressListener;
    private int totalSent;
    private int totalSize;

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
        sendPart(PART_BINARY, bundle.getApplication(), installId);
        if (bundle.getResources() != null) {
            sendPart(PART_RESOURCES, bundle.getResources(), installId);
        }
        if (bundle.getWorker() != null) {
            sendPart(PART_WORKER, bundle.getWorker(), installId);
        }
        publish("Launching " + header.getAppName());
    }

    private void insertAppMetadata(PebblePbwBundle.AppHeader header)
            throws IOException, InterruptedException {
        byte[] uuid = uuidBytes(header.getUuid());
        byte[] metadata = appMetadata(header, uuid);

        for (int attempt = 0; attempt < 8; attempt++) {
            int token = nextToken();
            ByteBuffer payload = ByteBuffer.allocate(1 + 2 + 1 + 1 + 16 + 2 + metadata.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            payload.put((byte) 0x01); // insert
            payload.putShort((short) token);
            payload.put((byte) BLOB_DATABASE_APP);
            payload.put((byte) uuid.length);
            payload.put(uuid);
            payload.putShort((short) metadata.length);
            payload.put(metadata);
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
        byte[] uuidBytes = uuidBytes(uuid);
        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) 0x01);
        start.put(uuidBytes);
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());

        byte[] fetch = link.awaitEndpoint(
                ENDPOINT_APP_FETCH,
                value -> value.length >= 21
                        && (value[0] & 0xff) == 0x01
                        && Arrays.equals(
                        Arrays.copyOfRange(value, 1, 17),
                        uuidBytes
                ),
                15_000
        );
        return ByteBuffer.wrap(fetch, 17, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void sendPart(int partType, byte[] object, int installId)
            throws IOException, InterruptedException {
        ByteBuffer init = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        init.put((byte) 0x01);
        init.putInt(object.length);
        init.put((byte) (partType | APP_INSTALL_FLAG));
        init.putInt(installId);
        PutBytesResponse initResponse = sendPutBytes(init.array(), null);
        int cookie = initResponse.cookie;

        for (int offset = 0; offset < object.length; offset += TRANSFER_CHUNK_BYTES) {
            int length = Math.min(TRANSFER_CHUNK_BYTES, object.length - offset);
            ByteBuffer put = ByteBuffer.allocate(9 + length).order(ByteOrder.BIG_ENDIAN);
            put.put((byte) 0x02);
            put.putInt(cookie);
            put.putInt(length);
            put.put(object, offset, length);
            sendPutBytes(put.array(), cookie);
            totalSent += length;
            publishProgress();
            Thread.sleep(4);
        }

        ByteBuffer commit = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
        commit.put((byte) 0x03);
        commit.putInt(cookie);
        commit.putInt(stm32Crc32(object));
        sendPutBytes(commit.array(), cookie);

        ByteBuffer install = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        install.put((byte) 0x05);
        install.putInt(cookie);
        sendPutBytes(install.array(), cookie);
    }

    private PutBytesResponse sendPutBytes(byte[] payload, Integer expectedCookie)
            throws IOException, InterruptedException {
        link.sendPebblePacket(ENDPOINT_PUT_BYTES, payload);
        byte[] response = link.awaitEndpoint(
                ENDPOINT_PUT_BYTES,
                value -> value.length >= 5
                        && (expectedCookie == null
                        || ByteBuffer.wrap(value, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt()
                        == expectedCookie),
                12_000
        );
        int result = response[0] & 0xff;
        int cookie = ByteBuffer.wrap(response, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (result != PUT_BYTES_ACK) {
            throw new IOException("Pebble NACKed PutBytes for cookie " + Integer.toUnsignedString(cookie));
        }
        return new PutBytesResponse(cookie);
    }

    private void publishProgress() {
        if (progressListener == null) {
            return;
        }
        int percent = totalSize <= 0 ? 0 : Math.min(100, Math.round(totalSent * 100f / totalSize));
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
        metadata.put((byte) 0); // app face background colour
        metadata.put((byte) 0); // app face template id

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
