package com.manufacttest.nullwrist.time2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Pebble serial framing: QEMU control packets and nested Pebble Protocol.
 *
 * Source reference: coredevices/qemu hw/arm/pebble_control.c and
 * coredevices/libpebble2 transport/qemu/protocol.py.
 *
 * Every integer in the two outer headers is NETWORK BIG ENDIAN.
 * Payload-specific structures (AppMetadata / BlobDB) may be LE.
 */
public final class EmeryPebbleWire {
    public static final int SPP=1;
    public static final int MAX_CONTROL_PAYLOAD=2048;
    public static final int MAX_PEBBLE_PAYLOAD=MAX_CONTROL_PAYLOAD-4;
    public static final int MAX_INBOUND_PEBBLE=65535;
    private static final int HEADER=0xFEED, FOOTER=0xBEEF;
    private EmeryPebbleWire(){}

    static int u16(byte[] src,int off){
        return ((src[off]&255)<<8)|(src[off+1]&255);
    }
    static void be16(ByteArrayOutputStream out,int value){
        out.write((value>>8)&255);out.write(value&255);
    }
    static void le16(ByteArrayOutputStream out,int value){
        out.write(value&255);out.write((value>>8)&255);
    }
    static void le32(ByteArrayOutputStream out,long value){
        for(int i=0;i<4;i++)out.write((int)(value>>(8*i))&255);
    }
    static void uuid(ByteArrayOutputStream out,java.util.UUID id){
        long hi=id.getMostSignificantBits(),lo=id.getLeastSignificantBits();
        for(int i=7;i>=0;i--)out.write((int)(hi>>(i*8))&255);
        for(int i=7;i>=0;i--)out.write((int)(lo>>(i*8))&255);
    }
    static byte[] protocol(int endpoint,byte[] data){
        if(data.length>MAX_INBOUND_PEBBLE)throw new IllegalArgumentException("Pebble message oversized");
        ByteArrayOutputStream out=new ByteArrayOutputStream(data.length+4);
        be16(out,data.length);be16(out,endpoint);out.write(data,0,data.length);
        return out.toByteArray();
    }
    public static byte[] qemu(int type,byte[] data){
        if(data.length>MAX_CONTROL_PAYLOAD)throw new IllegalArgumentException("QEMU payload >2048");
        ByteArrayOutputStream out=new ByteArrayOutputStream(data.length+8);
        be16(out,HEADER);be16(out,type);be16(out,data.length);
        out.write(data,0,data.length);be16(out,FOOTER);
        return out.toByteArray();
    }
    public static byte[][] splitSpp(int endpoint,byte[] data){
        byte[] packet=protocol(endpoint,data);
        int count=(packet.length+MAX_CONTROL_PAYLOAD-1)/MAX_CONTROL_PAYLOAD;
        byte[][] pieces=new byte[count][];
        for(int i=0;i<count;i++){
            int pos=i*MAX_CONTROL_PAYLOAD;
            pieces[i]=qemu(SPP,Arrays.copyOfRange(packet,pos,
                    Math.min(packet.length,pos+MAX_CONTROL_PAYLOAD)));
        }
        return pieces;
    }
    public static byte[] appVersionReply(){
        // libpebble2.protocol.system.AppVersionResponse, endpoint 0x11
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        data.write(1);
        le32be(data,0xFFFFFFFFL);le32be(data,0x80000000L);
        le32be(data,50);data.write(2);data.write(3);data.write(0);data.write(0);
        le64be(data,-1L);
        return data.toByteArray();
    }
    private static void le32be(ByteArrayOutputStream out,long value){
        for(int i=3;i>=0;i--)out.write((int)(value>>(8*i))&255);
    }
    private static void le64be(ByteArrayOutputStream out,long value){
        for(int i=7;i>=0;i--)out.write((int)(value>>(8*i))&255);
    }
    public static long stm32Crc(byte[] data){
        long crc=0xFFFFFFFFL;
        for(int i=0;i<data.length;i+=4){
            int count=Math.min(4,data.length-i);
            long word=0;
            if(count==4){
                for(int j=0;j<4;j++)word|=(long)(data[i+j]&255)<<(j*8);
            }else{
                for(int j=0;j<count;j++)word=(word<<8)|(data[i+j]&255);
            }
            crc ^= word;
            for(int k=0;k<32;k++){
                if((crc&0x80000000L)!=0)crc=((crc<<1)^0x04C11DB7L)&0xFFFFFFFFL;
                else crc=(crc<<1)&0xFFFFFFFFL;
            }
        }
        return crc;
    }
    public static final class Packet {
        public final int endpoint;
        public final byte[] payload;
        public Packet(int endpoint,byte[] payload){this.endpoint=endpoint;this.payload=payload;}
    }

    /**
     * Persistent accumulation handles QEMU SPP fragmentation and multiple
     * nested Pebble frames arriving in a single QEMU control frame.
     */
    public static final class Decoder {
        private byte[] waiting=new byte[0];
        public void append(byte[] spp)throws IOException{
            if(spp.length+waiting.length>MAX_INBOUND_PEBBLE+MAX_CONTROL_PAYLOAD)
                throw new IOException("Excess Pebble protocol buffering");
            byte[] joined=Arrays.copyOf(waiting,waiting.length+spp.length);
            System.arraycopy(spp,0,joined,waiting.length,spp.length);
            waiting=joined;
        }
        public Packet poll()throws IOException{
            if(waiting.length<4)return null;
            int n=u16(waiting,0);
            if(n>MAX_INBOUND_PEBBLE)throw new IOException("Oversized nested Pebble packet");
            if(waiting.length<n+4)return null;
            Packet packet=new Packet(u16(waiting,2),
                    Arrays.copyOfRange(waiting,4,4+n));
            waiting=Arrays.copyOfRange(waiting,n+4,waiting.length);
            return packet;
        }
    }
}
