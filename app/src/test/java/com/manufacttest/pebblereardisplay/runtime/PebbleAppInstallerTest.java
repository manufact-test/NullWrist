package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

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

    private static void assertCrc(long expectedUnsigned, byte[] value) {
        assertEquals(expectedUnsigned, Integer.toUnsignedLong(PebbleAppInstaller.stm32Crc32(value)));
    }
}
