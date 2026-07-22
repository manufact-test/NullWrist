package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RuntimeRestartBackoffTest {
    @Test
    public void delayGrowsButRemainsBounded() {
        assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(1));
        assertEquals(4_000L, RuntimeRestartBackoff.delayMillis(2));
        assertEquals(8_000L, RuntimeRestartBackoff.delayMillis(3));
        assertEquals(16_000L, RuntimeRestartBackoff.delayMillis(4));
        assertEquals(32_000L, RuntimeRestartBackoff.delayMillis(5));
        assertEquals(60_000L, RuntimeRestartBackoff.delayMillis(6));
        assertEquals(60_000L, RuntimeRestartBackoff.delayMillis(20));
    }

    @Test
    public void invalidFailureCountUsesFirstDelay() {
        assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(0));
        assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(-4));
    }
}
