package com.manufacttest.pebblereardisplay.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DisplayUtilsTest {
    @Test
    public void recognizesTitanSizedRearWindow() {
        assertTrue(DisplayUtils.isCompactRearBounds(410, 502));
        assertTrue(DisplayUtils.isCompactRearBounds(720, 900));
    }

    @Test
    public void rejectsNormalPhoneWindow() {
        assertFalse(DisplayUtils.isCompactRearBounds(1080, 2400));
        assertFalse(DisplayUtils.isCompactRearBounds(900, 900));
        assertFalse(DisplayUtils.isCompactRearBounds(0, 502));
    }
}