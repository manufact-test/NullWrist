package com.manufacttest.pebblereardisplay.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WatchfaceMutationPolicyTest {
    @Test
    public void lastWatchfaceCannotBeDeleted() {
        assertEquals(
                WatchfaceMutationPolicy.DeleteDecision.KEEP_LAST,
                WatchfaceMutationPolicy.evaluate(1, false, false)
        );
    }

    @Test
    public void activeOrSelectedWatchfaceMustBeSwitchedFirst() {
        assertEquals(
                WatchfaceMutationPolicy.DeleteDecision.SWITCH_FIRST,
                WatchfaceMutationPolicy.evaluate(4, true, false)
        );
        assertEquals(
                WatchfaceMutationPolicy.DeleteDecision.SWITCH_FIRST,
                WatchfaceMutationPolicy.evaluate(4, false, true)
        );
    }

    @Test
    public void inactiveWatchfaceCanBeDeleted() {
        assertEquals(
                WatchfaceMutationPolicy.DeleteDecision.ALLOW,
                WatchfaceMutationPolicy.evaluate(4, false, false)
        );
    }
}
