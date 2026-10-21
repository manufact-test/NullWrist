package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportMapperTest {
    @Test public void titan2Letterbox() {
        ViewportMapper map=ViewportMapper.fit(200,228,410,502);
        assertEquals(0,map.left,0.01f);
        assertEquals(17.3f,map.top,0.05f);
        assertEquals(410f,map.right,0.01f);
        assertEquals(484.7f,map.bottom,0.05f);
        assertNull(map.toPebble(30,0));
        assertNull(map.toPebble(30,500));
    }
    @Test public void centerAndCorners() {
        ViewportMapper map=ViewportMapper.fit(200,228,410,502);
        assertEquals(100,map.toPebble(205,251).x);
        assertEquals(114,map.toPebble(205,251).y);
        assertEquals(0,map.toPebble(0,map.top).x);
        assertEquals(0,map.toPebble(0,map.top).y);
        assertEquals(199,map.toPebble(409.9f,map.bottom-.01f).x);
        assertEquals(227,map.toPebble(409.9f,map.bottom-.01f).y);
    }
    @Test public void rejectsInvalidInputs() {
        ViewportMapper map=ViewportMapper.fit(200,228,200,228);
        assertNull(map.toPebble(Float.NaN,30));
        assertNull(map.toPebble(200,50));
        assertNull(map.toPebble(10,-1));
        try { ViewportMapper.fit(200,228,0,500); fail("should reject zero width"); }
        catch (IllegalArgumentException expected) { /* correct */ }
    }
}
