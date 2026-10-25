package com.manufacttest.nullwrist.time2;

import java.io.IOException;
import java.util.Arrays;

/**
 * Stateful FEED/type/length/payload/BEEF decoder for QEMU serial.
 * Android socket reads may stop anywhere in a header, payload or footer.
 * A partially delivered frame is NOT an error and never gets discarded on
 * an idle/transaction timeout. Protocol corruption is still fatal.
 */
final class EmeryControlFramer {
    static final int MAX_BUFFER=16 * 1024;
    private final byte[] pending=new byte[MAX_BUFFER];
    private int used;

    static final class Frame {
        final int type;
        final byte[] payload;
        Frame(int type,byte[] payload){this.type=type;this.payload=payload;}
    }

    void append(byte[] bytes,int count)throws IOException{
        if(count<0 || count>bytes.length || used+count>pending.length)
            throw new IOException("Oversized buffered QEMU serial input");
        System.arraycopy(bytes,0,pending,used,count);
        used+=count;
    }

    Frame poll()throws IOException{
        if(used<6)return null;
        if(EmeryPebbleWire.u16(pending,0)!=0xFEED)
            throw new IOException("Pebble/QEMU control signature mismatch");
        int type=EmeryPebbleWire.u16(pending,2);
        int length=EmeryPebbleWire.u16(pending,4);
        if(length>EmeryPebbleWire.MAX_CONTROL_PAYLOAD)
            throw new IOException("Oversized QEMU serial frame: "+length);
        int total=6+length+2;
        if(used<total)return null;
        if(EmeryPebbleWire.u16(pending,6+length)!=0xBEEF)
            throw new IOException("Pebble/QEMU control footer mismatch");
        byte[] body=Arrays.copyOfRange(pending,6,6+length);
        System.arraycopy(pending,total,pending,0,used-total);
        used-=total;
        return new Frame(type,body);
    }
}
