package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RuntimeRestartBackoffTest {
    @Test
    public void delayGrowsButRemainsBounded() {
        assertEquals(15_000L, RuntimeRestartBackoff.delayMillis(1));
        assertEquals(30_000L, RuntimeRestartBackoff.delayMillis(2));
        assertEquals(60_000L, RuntimeRestartBackoff.delayMillis(3));
        assertEquals(120_000L, RuntimeRestartBackoff.delayMillis(4));
        assertEquals(240_000L, RuntimeRestartBackoff.delayMillis(5));
        assertEquals(300_000L, RuntimeRestartBackoff.delayMillis(6));
        assertEquals(300_000L, RuntimeRestartBackoff.delayMillis(20));
    }

    @Test
    public void invalidFailureCountUsesFirstDelay() {
        assertEquals(15_000L, RuntimeRestartBackoff.delayMillis(0));
        assertEquals(15_000L, RuntimeRestartBackoff.delayMillis(-4));
    }
}
