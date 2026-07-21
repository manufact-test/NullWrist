package com.manufacttest.pebblereardisplay.runtime;

import com.manufacttest.pebblereardisplay.data.AppPreferences;

/** Pure guard for task-removal recovery. System-level force stop is intentionally excluded. */
final class RuntimeRecoveryPolicy {
    private RuntimeRecoveryPolicy() {
    }

    static boolean shouldRecover(
            AppPreferences.RuntimeMode mode,
            boolean hasSelectedWatchface
    ) {
        return mode == AppPreferences.RuntimeMode.RELIABLE && hasSelectedWatchface;
    }
}
