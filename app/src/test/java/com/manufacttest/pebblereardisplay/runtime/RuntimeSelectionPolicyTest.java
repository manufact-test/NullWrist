package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RuntimeSelectionPolicyTest {
    @Test
    public void selectionIsQueuedOnlyDuringUnpluggedScheduleSleep() {
        assertTrue(RuntimeSelectionPolicy.shouldQueue(
                RuntimePowerPolicy.Mode.SCHEDULED_FREEZE,
                false
        ));
        assertFalse(RuntimeSelectionPolicy.shouldQueue(
                RuntimePowerPolicy.Mode.SCHEDULED_FREEZE,
                true
        ));
        assertFalse(RuntimeSelectionPolicy.shouldQueue(
                RuntimePowerPolicy.Mode.RUNNING,
                false
        ));
        assertFalse(RuntimeSelectionPolicy.shouldQueue(
                RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE,
                false
        ));
    }

    @Test
    public void queuedSelectionRunsOnceWhenScheduleSleepEnds() {
        assertTrue(RuntimeSelectionPolicy.shouldApplyQueuedSelection(
                RuntimePowerPolicy.Mode.SCHEDULED_FREEZE,
                RuntimePowerPolicy.Mode.RUNNING,
                true
        ));
        assertTrue(RuntimeSelectionPolicy.shouldApplyQueuedSelection(
                RuntimePowerPolicy.Mode.SCHEDULED_FREEZE,
                RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE,
                true
        ));
        assertFalse(RuntimeSelectionPolicy.shouldApplyQueuedSelection(
                RuntimePowerPolicy.Mode.SCHEDULED_FREEZE,
                RuntimePowerPolicy.Mode.RUNNING,
                false
        ));
        assertFalse(RuntimeSelectionPolicy.shouldApplyQueuedSelection(
                RuntimePowerPolicy.Mode.RUNNING,
                RuntimePowerPolicy.Mode.RUNNING,
                true
        ));
    }
}
