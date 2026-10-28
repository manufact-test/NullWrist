package com.manufacttest.nullwrist.time2;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Require a continuously confirmed PebbleOS RUNNING UUID for ten seconds. */
final class EmeryRunStateTracker {
    private final UUID expected;
    private final long stableNanos;
    private long firstMatching=-1;
    private int matchingSamples;

    EmeryRunStateTracker(UUID expected,long stableSeconds){
        this.expected=expected;
        this.stableNanos=TimeUnit.SECONDS.toNanos(stableSeconds);
    }

    /**
     * AppRunState message payload starts with 0x01 and 16 UUID bytes.
     * A mismatch resets continuity; matching first/last must be >=10s apart.
     */
    boolean observe(byte[] response,long atNanos){
        if(!expected.equals(EmeryGuestRunState.uuid(response))){
            firstMatching=-1;matchingSamples=0;return false;
        }
        if(firstMatching<0 || atNanos<firstMatching){
            firstMatching=atNanos;
            matchingSamples=1;
            return false;
        }
        matchingSamples++;
        return matchingSamples>=2 && atNanos-firstMatching>=stableNanos;
    }
}
