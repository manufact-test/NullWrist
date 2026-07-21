package com.manufacttest.pebblereardisplay.runtime;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Minimal Pebble Protocol connection carried inside the Pebble QEMU TCP framing. */
public final class PebbleProtocolLink implements Closeable {
    private static final int QEMU_HEADER = 0xFEED;
    private static final int QEMU_FOOTER = 0xBEEF;
    private static final int QEMU_PROTOCOL_SPP = 1;
    private static final int QEMU_PROTOCOL_BLUETOOTH = 3;
    private static final int MAX_QEMU_PAYLOAD = 2048;

    private static final int ENDPOINT_WATCH_VERSION = 0x0010;
    private static final int ENDPOINT_PHONE_APP_VERSION = 0x0011;

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final Object outputLock = new Object();
    private final Map<Integer, BlockingQueue<byte[]>> endpointQueues = new ConcurrentHashMap<>();
    private final ByteQueue qemuBytes = new ByteQueue();
    private final ByteQueue pebbleBytes = new ByteQueue();
    private final Thread readerThread;

    private volatile boolean running = true;
    private volatile IOException readerFailure;
    private volatile int qemuFramesReceived;
    private volatile int sppFramesReceived;
    private volatile int pebblePacketsReceived;
    private volatile int lastEndpoint = -1;

    private PebbleProtocolLink(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        readerThread = new Thread(this::readLoop, "PebbleQemuProtocol");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public static PebbleProtocolLink connect(String host, int port, long timeoutMillis)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        IOException lastError = null;
        PebbleProtocolLink link = null;

        while (System.nanoTime() < deadline && link == null) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), 800);
                link = new PebbleProtocolLink(socket);
            } catch (IOException error) {
                lastError = error;
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                Thread.sleep(120);
            }
        }

        if (link == null) {
            throw new IOException("Could not connect to Pebble QEMU protocol port", lastError);
        }

        try {
            long remaining = Math.max(2_000,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            link.initialise(remaining);
            return link;
        } catch (IOException | InterruptedException error) {
            link.close();
            throw error;
        }
    }

    public void sendPebblePacket(int endpoint, byte[] payload) throws IOException {
        ensureRunning();
        if (payload.length > 0xffff) {
            throw new IOException("Pebble packet is too large");
        }
        ByteBuffer framed = ByteBuffer.allocate(payload.length + 4).order(ByteOrder.BIG_ENDIAN);
        framed.putShort((short) payload.length);
        framed.putShort((short) endpoint);
        framed.put(payload);
        byte[] message = framed.array();
        for (int offset = 0; offset < message.length; offset += MAX_QEMU_PAYLOAD) {
            int length = Math.min(MAX_QEMU_PAYLOAD, message.length - offset);
            sendQemuPacket(QEMU_PROTOCOL_SPP, Arrays.copyOfRange(message, offset, offset + length));
        }
    }

    public byte[] awaitEndpoint(
            int endpoint,
            Predicate<byte[]> predicate,
            long timeoutMillis
    ) throws IOException, InterruptedException {
        BlockingQueue<byte[]> queue = endpointQueues.computeIfAbsent(
                endpoint,
                ignored -> new LinkedBlockingQueue<>()
        );
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            ensureRunning();
            long remainingNanos = deadline - System.nanoTime();
            byte[] payload = queue.poll(
                    Math.max(1, Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 250)),
                    TimeUnit.MILLISECONDS
            );
            if (payload != null && (predicate == null || predicate.test(payload))) {
                return payload;
            }
        }
        throw new IOException(String.format(
                "Timed out waiting for Pebble endpoint 0x%04X (%s)",
                endpoint,
                diagnostics()
        ));
    }

    public String diagnostics() {
        String endpoint = lastEndpoint < 0
                ? "none"
                : String.format("0x%04X", lastEndpoint);
        return "qemuFrames=" + qemuFramesReceived
                + ", sppFrames=" + sppFramesReceived
                + ", pebblePackets=" + pebblePacketsReceived
                + ", lastEndpoint=" + endpoint;
    }

    /** Drops stale asynchronous responses before beginning a new protocol transaction. */
    public void clearEndpoint(int endpoint) {
        endpointQueues.computeIfAbsent(
                endpoint,
                ignored -> new LinkedBlockingQueue<>()
        ).clear();
    }

    public boolean isHealthy() {
        return running && readerFailure == null && !socket.isClosed();
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        readerThread.interrupt();
    }

    private void initialise(long timeoutMillis) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        IOException lastTimeout = null;

        while (System.nanoTime() < deadline) {
            ensureRunning();
            setBluetoothConnected(true);
            Thread.sleep(180);

            sendPebblePacket(ENDPOINT_WATCH_VERSION, new byte[]{0x00});
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            try {
                awaitEndpoint(
                        ENDPOINT_WATCH_VERSION,
                        payload -> payload.length > 0 && (payload[0] & 0xff) == 0x01,
                        Math.max(500, Math.min(2_000, remainingMillis))
                );
                return;
            } catch (IOException timeout) {
                if (!timeout.getMessage().startsWith("Timed out waiting")) {
                    throw timeout;
                }
                lastTimeout = timeout;
                Thread.sleep(180);
            }
        }

        throw new IOException(
                "Pebble phone protocol did not initialise (" + diagnostics() + ")",
                lastTimeout
        );
    }

    private void setBluetoothConnected(boolean connected) throws IOException {
        sendQemuPacket(QEMU_PROTOCOL_BLUETOOTH, new byte[]{(byte) (connected ? 1 : 0)});
    }

    private void sendPhoneAppVersionResponse() throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN);
        payload.put((byte) 0x01);
        payload.putInt(0xffffffff);
        payload.putInt(0x80000000);
        payload.putInt(50);
        payload.put((byte) 2);
        payload.put((byte) 3);
        payload.put((byte) 0);
        payload.put((byte) 0);
        payload.putLong(0xffffffffffffffffL);
        sendPebblePacket(ENDPOINT_PHONE_APP_VERSION, payload.array());
    }

    private void sendQemuPacket(int protocol, byte[] payload) throws IOException {
        ensureRunning();
        if (payload.length > 0xffff) {
            throw new IOException("QEMU packet is too large");
        }
        ByteBuffer frame = ByteBuffer.allocate(payload.length + 8).order(ByteOrder.BIG_ENDIAN);
        frame.putShort((short) QEMU_HEADER);
        frame.putShort((short) protocol);
        frame.putShort((short) payload.length);
        frame.put(payload);
        frame.putShort((short) QEMU_FOOTER);
        synchronized (outputLock) {
            output.write(frame.array());
            output.flush();
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[4096];
        try {
            while (running) {
                int read = input.read(buffer);
                if (read < 0) {
                    throw new EOFException("Pebble QEMU protocol socket closed");
                }
                if (read == 0) {
                    continue;
                }
                synchronized (qemuBytes) {
                    qemuBytes.append(buffer, 0, read);
                    parseQemuFrames();
                }
            }
        } catch (IOException error) {
            if (running) {
                readerFailure = error;
            }
        } finally {
            running = false;
        }
    }

    private void parseQemuFrames() throws IOException {
        while (qemuBytes.size() >= 8) {
            if (qemuBytes.unsignedShort(0) != QEMU_HEADER) {
                qemuBytes.discard(1);
                continue;
            }
            int protocol = qemuBytes.unsignedShort(2);
            int length = qemuBytes.unsignedShort(4);
            int total = length + 8;
            if (qemuBytes.size() < total) {
                return;
            }
            if (qemuBytes.unsignedShort(6 + length) != QEMU_FOOTER) {
                qemuBytes.discard(1);
                continue;
            }
            byte[] payload = qemuBytes.copy(6, length);
            qemuBytes.discard(total);
            qemuFramesReceived++;
            if (protocol == QEMU_PROTOCOL_SPP) {
                sppFramesReceived++;
                synchronized (pebbleBytes) {
                    pebbleBytes.append(payload, 0, payload.length);
                    parsePebbleFrames();
                }
            }
        }
    }

    private void parsePebbleFrames() throws IOException {
        while (pebbleBytes.size() >= 4) {
            int length = pebbleBytes.unsignedShort(0);
            int total = length + 4;
            if (pebbleBytes.size() < total) {
                return;
            }
            int endpoint = pebbleBytes.unsignedShort(2);
            byte[] payload = pebbleBytes.copy(4, length);
            pebbleBytes.discard(total);
            pebblePacketsReceived++;
            lastEndpoint = endpoint;

            if (endpoint == ENDPOINT_PHONE_APP_VERSION
                    && payload.length > 0
                    && (payload[0] & 0xff) == 0) {
                sendPhoneAppVersionResponse();
            }
            endpointQueues.computeIfAbsent(
                    endpoint,
                    ignored -> new LinkedBlockingQueue<>()
            ).offer(payload);
        }
    }

    private void ensureRunning() throws IOException {
        if (readerFailure != null) {
            throw new IOException("Pebble protocol reader failed", readerFailure);
        }
        if (!running || socket.isClosed()) {
            throw new IOException("Pebble protocol connection is closed");
        }
    }

    private static final class ByteQueue {
        private byte[] data = new byte[8192];
        private int start;
        private int end;

        int size() {
            return end - start;
        }

        void append(byte[] source, int offset, int length) {
            ensureCapacity(length);
            System.arraycopy(source, offset, data, end, length);
            end += length;
        }

        int unsignedShort(int offset) {
            int index = start + offset;
            return ((data[index] & 0xff) << 8) | (data[index + 1] & 0xff);
        }

        byte[] copy(int offset, int length) {
            return Arrays.copyOfRange(data, start + offset, start + offset + length);
        }

        void discard(int length) {
            start += length;
            if (start == end) {
                start = 0;
                end = 0;
            } else if (start > data.length / 2) {
                compact();
            }
        }

        private void ensureCapacity(int additional) {
            if (data.length - end >= additional) {
                return;
            }
            compact();
            if (data.length - end >= additional) {
                return;
            }
            int required = size() + additional;
            int newLength = data.length;
            while (newLength < required) {
                newLength *= 2;
            }
            data = Arrays.copyOf(data, newLength);
        }

        private void compact() {
            int length = size();
            System.arraycopy(data, start, data, 0, length);
            start = 0;
            end = length;
        }
    }
}
