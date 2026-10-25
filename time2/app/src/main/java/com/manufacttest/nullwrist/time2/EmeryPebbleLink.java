package com.manufacttest.nullwrist.time2;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
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
    private final EmeryControlFramer control=new EmeryControlFramer();
    private final byte[] readBuffer=new byte[4096];
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
        while(!closed && System.nanoTime()<deadline){
            EmeryPebbleWire.Packet nested=decoder.poll();
            if(nested!=null)return nested;
            EmeryControlFramer.Frame frame=control.poll();
            if(frame!=null){
                if(frame.type==EmeryPebbleWire.SPP){
                    decoder.append(frame.payload);
                }
                continue;
            }
            try{
                int count=input.read(readBuffer);
                if(count<0)throw new IOException("QEMU serial peer closed");
                if(count>0)control.append(readBuffer,count);
            }catch(SocketTimeoutException idle){
                // QEMU is asynchronous. An idle read is not a broken serial
                // packet, and every buffered partial frame survives the wait.
            }catch(IOException candidate){
                String message=candidate.getMessage();
                if(!closed && ("Try again".equals(message) ||
                        "EAGAIN".equals(message) ||
                        "Resource temporarily unavailable".equals(message)))continue;
                throw candidate;
            }
        }
        return null;
    }

    @Override public void close()throws IOException {
        closed=true;socket.close();
    }
}
