package com.manufacttest.pebblereardisplay.runtime;

/** Pure decisions for selections made while PebbleOS is frozen by Night Mode. */
final class RuntimeSelectionPolicy {
    private RuntimeSelectionPolicy() {
    }

    static boolean shouldQueue(RuntimePowerPolicy.Mode mode, boolean charging) {
        return mode == RuntimePowerPolicy.Mode.SCHEDULED_FREEZE && !charging;
    }

    static boolean shouldApplyQueuedSelection(
            RuntimePowerPolicy.Mode previousMode,
            RuntimePowerPolicy.Mode currentMode,
            boolean selectionQueued
    ) {
        return selectionQueued
                && previousMode == RuntimePowerPolicy.Mode.SCHEDULED_FREEZE
                && currentMode != RuntimePowerPolicy.Mode.SCHEDULED_FREEZE;
    }
}
