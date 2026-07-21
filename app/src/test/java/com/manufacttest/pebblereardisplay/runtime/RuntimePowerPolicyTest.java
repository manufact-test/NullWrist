package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RuntimePowerPolicyTest {
    @Test
    public void sameStartAndEndDisablesFreezeWindow() {
        assertFalse(RuntimePowerPolicy.isInsideSchedule(0, 120, 120));
        assertFalse(RuntimePowerPolicy.isInsideSchedule(300, 120, 120));
    }

    @Test
    public void daytimeWindowUsesInclusiveStartExclusiveEnd() {
        assertFalse(RuntimePowerPolicy.isInsideSchedule(479, 480, 1020));
        assertTrue(RuntimePowerPolicy.isInsideSchedule(480, 480, 1020));
        assertTrue(RuntimePowerPolicy.isInsideSchedule(1019, 480, 1020));
        assertFalse(RuntimePowerPolicy.isInsideSchedule(1020, 480, 1020));
    }

    @Test
    public void overnightWindowCrossesMidnight() {
        assertTrue(RuntimePowerPolicy.isInsideSchedule(1380, 1320, 420));
        assertTrue(RuntimePowerPolicy.isInsideSchedule(60, 1320, 420));
        assertFalse(RuntimePowerPolicy.isInsideSchedule(720, 1320, 420));
    }
}
