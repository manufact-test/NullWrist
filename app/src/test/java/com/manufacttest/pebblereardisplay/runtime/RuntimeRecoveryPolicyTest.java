package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.manufacttest.pebblereardisplay.data.AppPreferences;

import org.junit.Test;

public final class RuntimeRecoveryPolicyTest {
    @Test
    public void reliableModeRecoversOnlyWhenAWatchfaceIsSelected() {
        assertTrue(RuntimeRecoveryPolicy.shouldRecover(
                AppPreferences.RuntimeMode.RELIABLE,
                true
        ));
        assertFalse(RuntimeRecoveryPolicy.shouldRecover(
                AppPreferences.RuntimeMode.RELIABLE,
                false
        ));
    }

    @Test
    public void silentModeDoesNotRecreateItselfAfterClearAll() {
        assertFalse(RuntimeRecoveryPolicy.shouldRecover(
                AppPreferences.RuntimeMode.SILENT,
                true
        ));
    }
}
