package com.manufacttest.nullwrist.time2;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.junit.Assert.*;

public class EmeryGuestRunStateTest {
    private static byte[] state(UUID uuid){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(1);EmeryPebbleWire.uuid(out,uuid);
        return out.toByteArray();
    }
    @Test public void correctUuidAndLauncher(){
        UUID uuid=UUID.fromString("1e80e881-a883-45ed-8179-447f9e39668d");
        assertEquals(uuid,EmeryGuestRunState.uuid(state(uuid)));
        assertTrue(EmeryGuestRunState.valid(state(new UUID(0,0))));
        assertNull(EmeryGuestRunState.uuid(state(new UUID(0,0))));
        assertNull(EmeryGuestRunState.uuid(new byte[]{3}));
        assertFalse(EmeryGuestRunState.valid(new byte[]{3}));
    }
    @Test public void staleSampleCannotBecomeCurrent(){
        assertTrue(EmeryGuestRunState.fresh(true,1000,5000));
        assertFalse(EmeryGuestRunState.fresh(true,1000,18000));
        assertFalse(EmeryGuestRunState.fresh(false,1000,1010));
        assertFalse(EmeryGuestRunState.fresh(true,0,1010));
        assertFalse(EmeryGuestRunState.fresh(true,1010,1000));
    }
}
