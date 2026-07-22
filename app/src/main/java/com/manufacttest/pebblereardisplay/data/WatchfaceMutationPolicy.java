package com.manufacttest.pebblereardisplay.data;

/** Keeps destructive library changes away from the currently running PBW. */
public final class WatchfaceMutationPolicy {
    public enum DeleteDecision {
        ALLOW,
        KEEP_LAST,
        SWITCH_FIRST
    }

    private WatchfaceMutationPolicy() {
    }

    public static DeleteDecision evaluate(
            int watchfaceCount,
            boolean selected,
            boolean active
    ) {
        if (watchfaceCount <= 1) {
            return DeleteDecision.KEEP_LAST;
        }
        if (selected || active) {
            return DeleteDecision.SWITCH_FIRST;
        }
        return DeleteDecision.ALLOW;
    }
}
