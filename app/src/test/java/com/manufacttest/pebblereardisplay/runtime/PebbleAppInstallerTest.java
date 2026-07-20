package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PebbleAppInstallerTest {
    @Test
    public void crcMatchesPebblePutBytesVectors() {
        assertCrc(0xffffffffL, new byte[0]);
        assertCrc(0x6f60065bL, "a".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xd1dfbb34L, "ab".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xeb13e4f7L, "abc".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xa62f1c36L, "abcd".getBytes(StandardCharsets.UTF_8));
        assertCrc(0x169f68cbL, "hello world".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xa8e4ad71L, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    }

    @Test
    public void appRunStateRequiresRunningAndExactUuid() {
        UUID expected = UUID.fromString("13371337-0ebc-456b-a94f-6d07f39de93d");
        UUID other = UUID.fromString("13371337-6896-46c3-ab9c-0f4d5a2c421d");

        assertTrue(PebbleAppInstaller.isRunningStateFor(runState(1, expected), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(runState(2, expected), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(runState(1, other), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(new byte[]{1}, expected));
    }

    @Test
    public void putBytesAcceptsMatchingAckAndTokenZeroBusyNack() {
        byte[] ack = putBytesResponse(1, 0x11223344);
        byte[] busy = putBytesResponse(2, 0);
        byte[] staleAck = putBytesResponse(1, 0x55667788);
        byte[] staleNack = putBytesResponse(2, 0x55667788);

        assertTrue(PebbleAppInstaller.isPutBytesResponseFor(ack, null));
        assertTrue(PebbleAppInstaller.isPutBytesResponseFor(ack, 0x11223344));
        assertTrue(PebbleAppInstaller.isPutBytesResponseFor(busy, 0x11223344));
        assertFalse(PebbleAppInstaller.isPutBytesResponseFor(staleAck, 0x11223344));
        assertFalse(PebbleAppInstaller.isPutBytesResponseFor(staleNack, 0x11223344));
        assertEquals(0x11223344, PebbleAppInstaller.putBytesCookie(ack));
    }

    private static byte[] runState(int state, UUID uuid) {
        ByteBuffer payload = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN);
        payload.put((byte) state);
        payload.putLong(uuid.getMostSignificantBits());
        payload.putLong(uuid.getLeastSignificantBits());
        return payload.array();
    }

    private static byte[] putBytesResponse(int result, int token) {
        return ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) result)
                .putInt(token)
                .array();
    }

    private static void assertCrc(long expectedUnsigned, byte[] value) {
        assertEquals(expectedUnsigned, Integer.toUnsignedLong(PebbleAppInstaller.stm32Crc32(value)));
    }
}
