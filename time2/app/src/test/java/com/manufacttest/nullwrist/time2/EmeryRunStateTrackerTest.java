package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class EmeryRunStateTrackerTest {
    private byte[] response(UUID uuid){
        byte[] bytes=new byte[17];
        bytes[0]=1;
        java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
        EmeryPebbleWire.uuid(buffer,uuid);
        System.arraycopy(buffer.toByteArray(),0,bytes,1,16);
        return bytes;
    }

    @Test public void rejectsEarlyRunningAndRequiresTenRealSeconds(){
        UUID uuid=UUID.randomUUID();
        EmeryRunStateTracker tracker=new EmeryRunStateTracker(uuid,10);
        long t=TimeUnit.SECONDS.toNanos(100);
        assertFalse(tracker.observe(response(uuid),t));
        for(int i=1;i<=9;i++)assertFalse(tracker.observe(response(uuid),t+TimeUnit.SECONDS.toNanos(i)));
        assertTrue(tracker.observe(response(uuid),t+TimeUnit.SECONDS.toNanos(10)));
    }

    @Test public void mismatchResetsContinuity(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        EmeryRunStateTracker tracker=new EmeryRunStateTracker(a,10);
        long t=TimeUnit.SECONDS.toNanos(100);
        assertFalse(tracker.observe(response(a),t));
        assertFalse(tracker.observe(response(b),t+TimeUnit.SECONDS.toNanos(9)));
        assertFalse(tracker.observe(response(a),t+TimeUnit.SECONDS.toNanos(11)));
        assertTrue(tracker.observe(response(a),t+TimeUnit.SECONDS.toNanos(22)));
    }

    @Test public void missingAndMalformedRunStateReset(){
        UUID a=UUID.randomUUID();
        EmeryRunStateTracker tracker=new EmeryRunStateTracker(a,10);
        long t=TimeUnit.SECONDS.toNanos(2);
        assertFalse(tracker.observe(response(a),t));
        assertFalse(tracker.observe(new byte[]{0},t+TimeUnit.SECONDS.toNanos(5)));
        assertFalse(tracker.observe(response(a),t+TimeUnit.SECONDS.toNanos(10)));
        assertTrue(tracker.observe(response(a),t+TimeUnit.SECONDS.toNanos(20)));
    }
}
