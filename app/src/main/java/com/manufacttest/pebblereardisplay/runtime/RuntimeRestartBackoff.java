package com.manufacttest.pebblereardisplay.runtime;

/** Pure restart-delay policy used by the runtime watchdog. */
final class RuntimeRestartBackoff {
    private static final long BASE_DELAY_MILLIS = 15_000L;
    private static final long MAX_DELAY_MILLIS = 5L * 60L * 1_000L;

    private RuntimeRestartBackoff() {
    }

    static long delayMillis(int consecutiveFailures) {
        int failures = Math.max(1, consecutiveFailures);
        int shift = Math.min(failures - 1, 5);
        return Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << shift);
    }
}
