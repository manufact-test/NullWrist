package com.manufacttest.nullwrist.time2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stable read-only v2 framebuffer bridge for the native Emery QEMU exporter.
 *
 * The 64-byte header is little endian. Pixel payload is WIDTH*HEIGHT 32-bit
 * ARGB ints, NOT raw 8-bit Pebble GColor. The emulator applies its screen
 * brightness, transflective colour and vibration/shake before exporting.
 *
 * QEMU's single writer uses a seqlock: odd means a frame is being written;
 * an even positive value means finished. A reader accepts the frame only
 * when the sequence is identical before and after copying the pixels.
 */
public final class EmeryFrameProtocol {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 228;
    public static final int MAGIC = 0x50424642; // PBFB
    public static final int VERSION = 2;
    public static final int FORMAT_ARGB8888 = 2;
    public static final int HEADER_BYTES = 64;
    public static final int PIXEL_COUNT = WIDTH * HEIGHT;
    public static final int PIXEL_BYTES = PIXEL_COUNT * 4;
    public static final int TOTAL_BYTES = HEADER_BYTES + PIXEL_BYTES;

    private EmeryFrameProtocol() {}

    /**
     * @return a complete, internally consistent frame, or null while QEMU
     * is publishing / if the shared file has not yet been initialized.
     */
    public static Frame read(File source) throws IOException {
        if (!source.isFile()) return null;
        try (RandomAccessFile reader = new RandomAccessFile(source, "r")) {
            if (reader.length() != TOTAL_BYTES) return null;
            byte[] headerBytes = new byte[HEADER_BYTES];
            reader.readFully(headerBytes);
            Header header = parseHeader(headerBytes);
            if (header == null) return null;

            byte[] payload = new byte[PIXEL_BYTES];
            reader.readFully(payload);

            reader.seek(24); // sequence field offset
            // RandomAccessFile.readInt is big-endian; native file is little.
            int after = Integer.reverseBytes(reader.readInt());
            if (after != header.sequence || (after & 1) != 0) return null;

            int[] pixels = new int[PIXEL_COUNT];
            ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                    .asIntBuffer().get(pixels);
            return new Frame(header.sequence, pixels);
        }
    }

    private static Header parseHeader(byte[] data) {
        if (data.length != HEADER_BYTES) return null;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getInt() != VERSION) return null;
        if (b.getInt() != WIDTH || b.getInt() != HEIGHT) return null;
        if (b.getInt() != WIDTH || b.getInt() != FORMAT_ARGB8888) return null;
        int sequence = b.getInt();
        if (sequence <= 0 || (sequence & 1) != 0) return null;
        return new Header(sequence);
    }

    private static final class Header {
        final int sequence;
        Header(int value) { sequence = value; }
    }

    public static final class Frame {
        public final int sequence;
        public final int[] argbPixels;
        Frame(int sequence, int[] argbPixels) {
            this.sequence = sequence;
            this.argbPixels = argbPixels;
        }
    }
}
