package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class EmeryControlFramerTest {
    @Test public void splitHeaderPayloadAndFooterSurviveIdlePolling()throws Exception{
        EmeryControlFramer parser=new EmeryControlFramer();
        byte[] data=EmeryPebbleWire.qemu(EmeryPebbleWire.SPP,
                EmeryPebbleWire.protocol(0x34,new byte[]{1,2,3,4,5,6}));
        for(int i=0;i<data.length;i++){
            parser.append(new byte[]{data[i]},1);
            if(i<data.length-1)assertNull(parser.poll());
        }
        EmeryControlFramer.Frame frame=parser.poll();
        assertNotNull(frame);
        assertEquals(EmeryPebbleWire.SPP,frame.type);
        EmeryPebbleWire.Decoder nested=new EmeryPebbleWire.Decoder();
        nested.append(frame.payload);
        assertEquals(0x34,nested.poll().endpoint);
        assertNull(parser.poll());
    }

    @Test public void multipleConcatenatedFramesAndPartialNextFrame()throws Exception{
        EmeryControlFramer parser=new EmeryControlFramer();
        byte[] first=EmeryPebbleWire.qemu(2,new byte[]{11});
        byte[] second=EmeryPebbleWire.qemu(1,new byte[]{42,43});
        byte[] third=EmeryPebbleWire.qemu(1,new byte[]{44});
        byte[] chunk=new byte[first.length+second.length+3];
        System.arraycopy(first,0,chunk,0,first.length);
        System.arraycopy(second,0,chunk,first.length,second.length);
        System.arraycopy(third,0,chunk,first.length+second.length,3);
        parser.append(chunk,chunk.length);
        assertEquals(2,parser.poll().type);
        assertArrayEquals(new byte[]{42,43},parser.poll().payload);
        assertNull(parser.poll());
        parser.append(java.util.Arrays.copyOfRange(third,3,third.length),third.length-3);
        assertArrayEquals(new byte[]{44},parser.poll().payload);
    }

    @Test public void corruptionAndOversizeFailClosed()throws Exception{
        EmeryControlFramer parser=new EmeryControlFramer();
        byte[] bad=EmeryPebbleWire.qemu(1,new byte[]{1,2});
        bad[0]=0;
        parser.append(bad,bad.length);
        try{parser.poll();fail("must reject corrupt FEED");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("signature"));}
        EmeryControlFramer other=new EmeryControlFramer();
        byte[] footer=EmeryPebbleWire.qemu(1,new byte[]{1,2});
        footer[footer.length-1]=0;
        other.append(footer,footer.length);
        try{other.poll();fail("must reject bad BEEF");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("footer"));}
        EmeryControlFramer oversized=new EmeryControlFramer();
        byte[] header=new byte[]{(byte)0xfe,(byte)0xed,0,1,9,0};
        oversized.append(header,header.length);
        try{oversized.poll();fail("must reject 2304-byte payload");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("Oversized"));}
    }
}
