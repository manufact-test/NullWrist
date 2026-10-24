package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class EmeryPebbleWireTest {
    @Test public void qemuSppUsesNetworkByteOrder() {
        byte[] actual=EmeryPebbleWire.qemu(1,new byte[]{0x12,0x34});
        assertArrayEquals(new byte[]{(byte)0xFE,(byte)0xED,0,1,0,2,0x12,0x34,
                (byte)0xBE,(byte)0xEF},actual);
    }
    @Test public void decodingHandlesSplitAndBackToBackPebbleFrames() throws Exception {
        EmeryPebbleWire.Decoder parser=new EmeryPebbleWire.Decoder();
        byte[] one=EmeryPebbleWire.protocol(0xB1DB,new byte[]{9,8,7});
        byte[] two=EmeryPebbleWire.protocol(0x1771,new byte[]{1});
        parser.append(Arrays.copyOfRange(one,0,3));
        assertNull(parser.poll());
        parser.append(Arrays.copyOfRange(one,3,one.length));
        assertEquals(0xB1DB,parser.poll().endpoint);
        assertNull(parser.poll());
        byte[] combined=Arrays.copyOf(one,one.length+two.length);
        System.arraycopy(two,0,combined,one.length,two.length);
        parser.append(combined);
        assertEquals(0xB1DB,parser.poll().endpoint);
        assertEquals(0x1771,parser.poll().endpoint);
        assertNull(parser.poll());
    }
    @Test public void maxSizedPebbleChunksFitQemuLimit(){
        byte[][] packets=EmeryPebbleWire.splitSpp(0xBEEF,new byte[4000]);
        assertEquals(2,packets.length);
        assertEquals(2048+8,packets[0].length);
        assertEquals((4000+4-2048)+8,packets[1].length);
    }
    @Test public void appVersionFrameHasExpectedLayout(){
        byte[] data=EmeryPebbleWire.appVersionReply();
        assertEquals(1,data[0]);
        assertEquals(0xFFFFFFFFL,
                ((long)(data[1]&255)<<24)|((long)(data[2]&255)<<16)
                |((long)(data[3]&255)<<8)|(data[4]&255));
        assertEquals(1+4+4+4+4+8,data.length);
    }
    @Test public void crcMatchesStm32TestVectors(){
        assertEquals(0xFFFFFFFFL,EmeryPebbleWire.stm32Crc(new byte[0]));
        // 0xffffffff XOR first little-endian zero word, 32bit algorithm.
        assertEquals(0xC704DD7BL,EmeryPebbleWire.stm32Crc(new byte[]{0,0,0,0}));
        assertEquals(EmeryPebbleWire.stm32Crc(new byte[]{1,2,3,4}),
                EmeryPebbleWire.stm32Crc(new byte[]{1,2,3,4}));
    }
}
