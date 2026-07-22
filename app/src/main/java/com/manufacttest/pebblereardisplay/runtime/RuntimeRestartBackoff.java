package com.manufacttest.pebblereardisplay.runtime;

/** Pure restart-delay policy used by the runtime watchdog. */
final class RuntimeRestartBackoff {
    private static final long BASE_DELAY_MILLIS = 2_000L;
    private static final long MAX_DELAY_MILLIS = 60_000L;

    private RuntimeRestartBackoff() {
    }

    static long delayMillis(int consecutiveFailures) {
        int failures = Math.max(1, consecutiveFailures);
        int shift = Math.min(failures - 1, 5);
        return Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << shift);
    }
}
