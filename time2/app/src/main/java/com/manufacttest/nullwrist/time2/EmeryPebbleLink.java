package com.manufacttest.nullwrist.time2;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated app-private UNIX Pebble-serial channel.
 * Different from QMP: serial index 1 in QEMU pebble_generic.c is intercepted
 * by pebble_control.c. Frames are FEED/type/length/.../BEEF (network order)
 * and encapsulate the watch's own endpoint protocol.
 */
public final class EmeryPebbleLink implements Closeable {
    private final LocalSocket socket=new LocalSocket();
    private final InputStream input;
    private final OutputStream output;
    private final EmeryPebbleWire.Decoder decoder=new EmeryPebbleWire.Decoder();
    private volatile boolean closed;

    public EmeryPebbleLink(String unixPath)throws IOException{
        if(unixPath==null || unixPath.length()==0)throw new IOException("Missing watch serial socket path");
        try{
            socket.connect(new LocalSocketAddress(unixPath,
                    LocalSocketAddress.Namespace.FILESYSTEM));
            socket.setSoTimeout(900);
            input=socket.getInputStream();
            output=socket.getOutputStream();
        }catch(IOException error){
            try{socket.close();}catch(IOException ignored){}
            throw error;
        }
    }

    public void send(int endpoint,byte[] data)throws IOException{
        if(closed)throw new IOException("Pebble serial bridge closed");
        for(byte[] piece:EmeryPebbleWire.splitSpp(endpoint,data)){
            output.write(piece);
            output.flush();
        }
    }

    public EmeryPebbleWire.Packet waitFor(int endpoint,int seconds)throws IOException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!closed && System.nanoTime()<deadline){
            EmeryPebbleWire.Packet packet=next(deadline);
            if(packet==null)continue;
            if(packet.endpoint==0x11&&packet.payload.length>0&&packet.payload[0]==0){
                // Device expects companion AppVersionResponse before app operations.
                send(0x11,EmeryPebbleWire.appVersionReply());
                continue;
            }
            if(packet.endpoint==endpoint)return packet;
            // Ignore asynchronous log/timeline/notification responses. A serial
            // worker owns installation; no concurrently issued watch requests.
        }
        throw new IOException("PebbleOS endpoint 0x"+Integer.toHexString(endpoint)+
                " timed out after "+seconds+" seconds");
    }

    /**
     * Drain asynchronous guest traffic after install without disconnecting the
     * watch's virtual phone. This also serves late PhoneAppVersion requests.
     * Idle timeouts return silently rather than dropping the link.
     */
    public void serviceOnce(int seconds)throws IOException {
        if(closed)return;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        EmeryPebbleWire.Packet packet=next(deadline);
        if(packet!=null && packet.endpoint==0x11 && packet.payload.length>0
                && packet.payload[0]==0){
            send(0x11,EmeryPebbleWire.appVersionReply());
        }
    }

    public EmeryPebbleWire.Packet transact(int endpoint,byte[] payload,
                                           int responseEndpoint,int seconds)throws IOException{
        send(endpoint,payload);
        return waitFor(responseEndpoint,seconds);
    }

    private EmeryPebbleWire.Packet next(long deadline)throws IOException{
        EmeryPebbleWire.Packet existing=decoder.poll();
        if(existing!=null)return existing;
        while(!closed && System.nanoTime()<deadline){
            byte[] header=readExactly(6,deadline);
            if(header==null)return null;
            int signature=EmeryPebbleWire.u16(header,0);
            int proto=EmeryPebbleWire.u16(header,2);
            int length=EmeryPebbleWire.u16(header,4);
            if(signature!=0xFEED)throw new IOException("Pebble/QEMU control signature mismatch");
            if(length>EmeryPebbleWire.MAX_CONTROL_PAYLOAD)
                throw new IOException("Oversized QEMU serial frame");
            byte[] body=readExactly(length+2,deadline);
            if(body==null)throw new IOException("QEMU serial body was not received");
            if(EmeryPebbleWire.u16(body,length)!=0xBEEF)
                throw new IOException("Pebble/QEMU control footer mismatch");
            if(proto!=EmeryPebbleWire.SPP)continue;
            decoder.append(Arrays.copyOf(body,length));
            EmeryPebbleWire.Packet parsed=decoder.poll();
            if(parsed!=null)return parsed;
        }
        return null;
    }

    private byte[] readExactly(int length,long deadline)throws IOException{
        byte[] buffer=new byte[length];
        int done=0;
        while(done<length){
            if(closed)throw new IOException("Emery link closed");
            if(System.nanoTime()>=deadline){
                if(done==0)return null; // no traffic during idle guest session
                throw new IOException("Partial QEMU serial frame timed out ("+done+"/"+length+")");
            }
            try{
                int n=input.read(buffer,done,length-done);
                if(n<0)throw new IOException("QEMU serial channel closed");
                done+=n;
            }catch(SocketTimeoutException retry){
                // Bounded timeouts permit interruption while the guest boots.
            }
        }
        return buffer;
    }

    @Override public void close()throws IOException {
        closed=true;socket.close();
    }
}
