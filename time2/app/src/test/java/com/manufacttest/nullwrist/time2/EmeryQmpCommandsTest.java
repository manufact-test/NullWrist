package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import static org.junit.Assert.*;

public class EmeryQmpCommandsTest {
    @Test public void qmpQuitMustBeSingleValidJsonCommand() throws Exception {
        org.json.JSONObject parsed=new org.json.JSONObject(EmeryQmpCommands.quit());
        assertEquals("quit",parsed.getString("execute"));
        assertEquals(1,parsed.length());
    }
    @Test public void mapsEachButtonToPebbleKey() {
        assertTrue(EmeryQmpCommands.button(EmeryQmpCommands.Button.BACK).contains("\"left\""));
        assertTrue(EmeryQmpCommands.button(EmeryQmpCommands.Button.SELECT).contains("\"right\""));
        assertTrue(EmeryQmpCommands.button(EmeryQmpCommands.Button.UP).contains("\"up\""));
        assertTrue(EmeryQmpCommands.button(EmeryQmpCommands.Button.DOWN).contains("\"down\""));
    }
    @Test public void touchSequenceAndCoordinates() {
        String down=EmeryQmpCommands.touch(EmeryQmpCommands.TouchPhase.DOWN,199,227);
        assertTrue(down.contains("\"down\":true"));
        assertTrue(down.contains("\"axis\":\"x\""));
        String move=EmeryQmpCommands.touch(EmeryQmpCommands.TouchPhase.MOVE,100,114);
        assertFalse(move.contains("\"down\""));
        String up=EmeryQmpCommands.touch(EmeryQmpCommands.TouchPhase.UP,199,227);
        assertTrue(up.contains("\"down\":false"));
        assertEquals(32685,EmeryQmpCommands.absForPixel(199,200));
    }
    @Test public void forbidsImpossibleCoordinates() {
        try { EmeryQmpCommands.absForPixel(200,200); fail("must reject"); }
        catch(IllegalArgumentException expected) {}
    }
}
