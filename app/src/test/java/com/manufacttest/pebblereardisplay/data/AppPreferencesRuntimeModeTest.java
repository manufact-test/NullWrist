package com.manufacttest.pebblereardisplay.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AppPreferencesRuntimeModeTest {
    @Test
    public void missingOrUnknownModeFallsBackToReliable() {
        assertEquals(
                AppPreferences.RuntimeMode.RELIABLE,
                AppPreferences.RuntimeMode.fromStoredValue(null)
        );
        assertEquals(
                AppPreferences.RuntimeMode.RELIABLE,
                AppPreferences.RuntimeMode.fromStoredValue("future-mode")
        );
    }

    @Test
    public void storedSilentModeIsRestored() {
        assertEquals(
                AppPreferences.RuntimeMode.SILENT,
                AppPreferences.RuntimeMode.fromStoredValue("silent")
        );
    }
}
