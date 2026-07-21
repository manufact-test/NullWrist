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
        // previousMode is intentionally not required here. A policy evaluation can observe
        // the wake transition while the runtime is busy and record RUNNING before it is safe
        // to apply the queued selection. The next serialized policy pass must still apply it.
        return selectionQueued
                && currentMode != RuntimePowerPolicy.Mode.SCHEDULED_FREEZE;
    }
}
