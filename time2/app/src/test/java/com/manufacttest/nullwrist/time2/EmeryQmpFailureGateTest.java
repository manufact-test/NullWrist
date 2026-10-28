package com.manufacttest.nullwrist.time2;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmeryQmpFailureGateTest {
    @Test public void alertsOnlyAfterThresholdAndOncePerIncident(){
        EmeryQmpFailureGate gate=new EmeryQmpFailureGate(3);
        assertFalse(gate.failed());
        assertFalse(gate.failed());
        assertTrue(gate.failed());
        assertFalse(gate.failed());
        assertFalse(gate.failed());
        assertTrue(gate.ok());
        assertFalse(gate.ok());
        assertFalse(gate.failed());
        assertFalse(gate.failed());
        assertTrue(gate.failed());
    }
    @Test public void recoveredShortGlitchDoesNotEscalate(){
        EmeryQmpFailureGate gate=new EmeryQmpFailureGate(3);
        gate.failed();
        assertFalse(gate.ok());
        assertFalse(gate.failed());
        assertFalse(gate.failed());
    }
}
