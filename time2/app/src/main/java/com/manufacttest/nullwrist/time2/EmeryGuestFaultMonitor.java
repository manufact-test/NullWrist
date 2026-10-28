package com.manufacttest.nullwrist.time2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bounded incremental scanner for diagnostic UART2 Pulse binary stream.
 * Literal guest crash messages are embedded in binary frames on this
 * firmware. This intentionally does NOT pretend to dehash all Pulse events.
 */
final class EmeryGuestFaultMonitor {
    private final File file;
    private long offset;
    private byte[] carry=new byte[0];

    EmeryGuestFaultMonitor(File file){this.file=file;}

    String nextFault(){
        try {
            if(!file.isFile())return null;
            long length=file.length();
            if(length<offset){offset=0;carry=new byte[0];}
            if(length==offset)return null;
            long remaining=Math.min(length-offset,65536);
            byte[] block=new byte[(int)remaining];
            try(FileInputStream in=new FileInputStream(file)){
                long skip=offset;
                while(skip>0){long n=in.skip(skip);if(n<=0)throw new IOException("UART2 skip");skip-=n;}
                int pos=0;
                while(pos<block.length){
                    int n=in.read(block,pos,block.length-pos);
                    if(n<0)break;
                    pos+=n;
                }
                if(pos!=block.length)return null;
            }
            offset+=block.length;
            byte[] joined=new byte[carry.length+block.length];
            System.arraycopy(carry,0,joined,0,carry.length);
            System.arraycopy(block,0,joined,carry.length,block.length);
            int retain=Math.min(120,joined.length);
            carry=new byte[retain];
            System.arraycopy(joined,joined.length-retain,carry,0,retain);
            String text=new String(joined,StandardCharsets.ISO_8859_1);
            if(text.contains("Stack overflow [task: App"))
                return "Stack overflow in PebbleOS app (see UART2)";
            if(text.contains("Memory Management Fault"))
                return "Memory Management Fault in PebbleOS (see UART2)";
            if(text.contains("App fault!"))
                return "App fault in PebbleOS (see UART2)";
            return null;
        }catch(IOException ignored){
            return null; // Do not crash emulator because diagnostics became unreadable.
        }
    }
}
