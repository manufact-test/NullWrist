package com.manufacttest.nullwrist.time2;

/** Bounded QMP health warning gate. Never reboots or resets a guest.
 * Static watchface frames are not counted as a failure.
 */
final class EmeryQmpFailureGate {
    private final int threshold;
    private int consecutive;
    private boolean alarm;
    EmeryQmpFailureGate(int threshold){
        if(threshold<1)throw new IllegalArgumentException("threshold");
        this.threshold=threshold;
    }
    synchronized boolean failed(){
        if(consecutive<1000)consecutive++;
        if(consecutive>=threshold && !alarm){
            alarm=true;
            return true;
        }
        return false;
    }
    synchronized boolean ok(){
        boolean recovered=alarm;
        consecutive=0;
        alarm=false;
        return recovered;
    }
}
