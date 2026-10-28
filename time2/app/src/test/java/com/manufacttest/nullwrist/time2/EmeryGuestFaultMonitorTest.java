package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class EmeryGuestFaultMonitorTest {
    @Test public void detectsFragmentedBinaryPulseFaultOnce() throws Exception{
        File f=File.createTempFile("emery-uart-fault-",".bin");
        try{
            EmeryGuestFaultMonitor mon=new EmeryGuestFaultMonitor(f);
            assertNull(mon.nextFault());
            try(FileOutputStream out=new FileOutputStream(f,true)){
                out.write(new byte[]{0x55,0x03,0x00});
                out.write("Stack over".getBytes(StandardCharsets.US_ASCII));
            }
            assertNull(mon.nextFault());
            try(FileOutputStream out=new FileOutputStream(f,true)){
                out.write("flow [task: App <Pixel Mine]".getBytes(StandardCharsets.US_ASCII));
            }
            assertEquals("Stack overflow in PebbleOS app (see UART2)",mon.nextFault());
            assertNull(mon.nextFault());
        }finally{f.delete();}
    }
    @Test public void ignoresNormalBootLog()throws Exception{
        File f=File.createTempFile("emery-uart-normal-",".bin");
        try{
            Files.write(f.toPath(),"Valid system resources found!".getBytes(StandardCharsets.UTF_8));
            assertNull(new EmeryGuestFaultMonitor(f).nextFault());
        }finally{f.delete();}
    }
}
