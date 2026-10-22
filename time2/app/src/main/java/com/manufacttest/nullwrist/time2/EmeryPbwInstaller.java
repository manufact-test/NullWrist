package com.manufacttest.nullwrist.time2;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/**
 * Official modern PebbleOS app-install handshake over QEMU UART1:
 *
 * BlobDB App insert (endpoint B1DB) -> AppRunStateStart (0034) ->
 * AppFetchRequest (1771) -> PutBytes (BEEF): INIT,PUT,COMMIT,INSTALL
 * for executable, optional resources and optional worker.
 *
 * References: coredevices/libpebble2/services/install.py and putbytes.py,
 * protocol apps.py, blobdb.py and transfers.py.
 *
 * Does not emulate a watchface; it asks the real PebbleOS guest to install.
 * Caller holds a single Ember QEMU serial link and private PBW copy.
 */
public final class EmeryPbwInstaller {
    public interface Progress { void update(String stage); }
    private static final int MAX_BINARY=20*1024*1024;
    private final EmeryPebbleLink link;
    private final Progress progress;
    private int token=0x2101;
    public EmeryPbwInstaller(EmeryPebbleLink link,Progress progress){
        this.link=link;this.progress=progress;
    }

    public void install(File pbw,String previouslyInstalledUuid)throws IOException{
        EmeryPackageIndex.PackageInfo info=EmeryPackageIndex.inspect(pbw);
        progress.update("PebbleOS: awaiting companion handshake...");
        EmeryPebbleWire.Packet v=link.transact(0x10,new byte[]{0},0x10,35);
        if(v.payload.length<2||v.payload[0]!=1)
            throw new IOException("Guest returned invalid WatchVersion response");
        // Re-installing a face while another runs can produce the native OS
        // "is not responding" crash dialog (see PebbleOS issue #2172).
        // Request graceful DEINIT instead of force-killing the running face.
        java.util.UUID stopping;
        try {stopping=java.util.UUID.fromString(
                previouslyInstalledUuid==null?info.uuid:previouslyInstalledUuid);}
        catch(IllegalArgumentException invalid) {stopping=java.util.UUID.fromString(info.uuid);}
        progress.update("PebbleOS: requesting graceful watchface stop...");
        ByteArrayOutputStream stop=new ByteArrayOutputStream(17);
        stop.write(2); EmeryPebbleWire.uuid(stop,stopping);
        link.send(0x34,stop.toByteArray());
        try{Thread.sleep(280);}catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("Graceful stop interrupted",interrupted);
        }

        try(ZipFile archive=new ZipFile(pbw)){
            String prefix=info.platform.equals("legacy")?"":info.platform+"/";
            JSONObject manifest=readManifest(archive,prefix+"manifest.json");
            JSONObject app=manifest.optJSONObject("application");
            if(app==null)throw new IOException("No PBW application in manifest");
            byte[] binary=read(archive,prefix,app.optString("name",""));
            byte[] header=new byte[120];
            System.arraycopy(binary,0,header,0,header.length);
            int sdkMajor=header[10]&255, sdkMinor=header[11]&255;
            int appMajor=header[12]&255,appMinor=header[13]&255;
            int icon=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(88);
            int flags=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(96);
            UUID uuid=UUID.fromString(info.uuid);

            // Install metadata in AppDB; successful response needed before
            // asking the guest to run/fetch it. This is one user-selected app.
            progress.update("PebbleOS: registering "+info.name+"...");
            ByteArrayOutputStream meta=new ByteArrayOutputStream();
            EmeryPebbleWire.uuid(meta,uuid);
            EmeryPebbleWire.le32(meta,flags&0xFFFFFFFFL);
            EmeryPebbleWire.le32(meta,icon&0xFFFFFFFFL);
            meta.write(appMajor);meta.write(appMinor);
            meta.write(sdkMajor);meta.write(sdkMinor);
            meta.write(0);meta.write(0);
            byte[] name=info.name.getBytes(StandardCharsets.UTF_8);
            meta.write(name,0,Math.min(name.length,95));
            for(int i=Math.min(name.length,95);i<96;i++)meta.write(0);
            byte[] blobValue=meta.toByteArray();

            ByteArrayOutputStream blob=new ByteArrayOutputStream();
            blob.write(1); // BlobCommand Insert
            int currentToken=token++;
            EmeryPebbleWire.le16(blob,currentToken);
            blob.write(2); // BlobDatabaseID.App
            blob.write(16);EmeryPebbleWire.uuid(blob,uuid);
            EmeryPebbleWire.le16(blob,blobValue.length);
            blob.write(blobValue,0,blobValue.length);
            EmeryPebbleWire.Packet ack=link.transact(0xB1DB,blob.toByteArray(),
                    0xB1DB,20);
            if(ack.payload.length<3||
                    (ack.payload[0]&255)!=currentToken%256||
                    (ack.payload[1]&255)!=(currentToken>>8)||
                    (ack.payload[2]&255)!=1)
                throw new IOException("Guest rejected PBW metadata (BlobDB "
                        +(ack.payload.length>=3?(ack.payload[2]&255):-1)+")");

            progress.update("PebbleOS: requesting AppFetch...");
            ByteArrayOutputStream start=new ByteArrayOutputStream();
            start.write(1);EmeryPebbleWire.uuid(start,uuid);
            EmeryPebbleWire.Packet fetch=link.transact(0x34,start.toByteArray(),
                    0x1771,30);
            if(fetch.payload.length<21 || fetch.payload[0]!=1)
                throw new IOException("Guest did not provide AppFetch request");
            byte[] expectedId=uuidBytes(uuid);
            for(int i=0;i<16;i++){
                if(fetch.payload[i+1]!=expectedId[i])
                    throw new IOException("Guest requested an unexpected PBW UUID");
            }
            int appId=ByteBuffer.wrap(fetch.payload,17,4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            progress.update("PebbleOS: installing executable...");
            putBytes(5,binary,appId);
            JSONObject res=manifest.optJSONObject("resources");
            if(res!=null){
                progress.update("PebbleOS: installing resources...");
                putBytes(4,read(archive,prefix,res.optString("name","")),appId);
            }
            JSONObject worker=manifest.optJSONObject("worker");
            if(worker!=null){
                progress.update("PebbleOS: installing background worker...");
                putBytes(7,read(archive,prefix,worker.optString("name","")),appId);
            }
            progress.update("PBW transfer complete: "+info.name+
                    " — checking if PebbleOS actually starts it");
        }catch(JSONException malformed){
            throw new IOException("Invalid platform manifest in PBW",malformed);
        }
    }

    private void putBytes(int type,byte[] payload,int appId)throws IOException {
        ByteArrayOutputStream init=new ByteArrayOutputStream();
        init.write(1);
        writeBe32(init,payload.length);
        init.write(type|0x80); // app-install-id present
        writeBe32(init,appId);
        EmeryPebbleWire.Packet response=link.transact(0xBEEF,init.toByteArray(),
                0xBEEF,20);
        int cookie=checkPutBytes(response);
        int sent=0,reported=0;
        while(sent<payload.length){
            int size=Math.min(2000,payload.length-sent);
            ByteArrayOutputStream chunk=new ByteArrayOutputStream(size+9);
            chunk.write(2);writeBe32(chunk,cookie);writeBe32(chunk,size);
            chunk.write(payload,sent,size);
            int ackCookie=checkPutBytes(link.transact(0xBEEF,chunk.toByteArray(),
                    0xBEEF,30));
            if(ackCookie!=cookie)throw new IOException("PBW transfer cookie mismatch");
            sent+=size;
            if(sent-reported>=16384||sent==payload.length){
                progress.update("PebbleOS: uploaded "+sent+"/"+payload.length+" bytes");
                reported=sent;
            }
            try{Thread.sleep(4);}catch(InterruptedException stop){
                Thread.currentThread().interrupt();throw new IOException("PBW installation interrupted",stop);
            }
        }
        ByteArrayOutputStream commit=new ByteArrayOutputStream();
        commit.write(3);writeBe32(commit,cookie);
        writeBe32(commit,EmeryPebbleWire.stm32Crc(payload));
        int commitCookie=checkPutBytes(link.transact(0xBEEF,commit.toByteArray(),
                0xBEEF,30));
        if(commitCookie!=cookie)throw new IOException("PBW commit cookie mismatch");
        ByteArrayOutputStream install=new ByteArrayOutputStream();
        install.write(5);writeBe32(install,cookie);
        int installCookie=checkPutBytes(link.transact(0xBEEF,install.toByteArray(),
                0xBEEF,30));
        if(installCookie!=cookie)throw new IOException("PBW install cookie mismatch");
    }

    private static int checkPutBytes(EmeryPebbleWire.Packet ack)throws IOException{
        if(ack.payload.length<5||ack.payload[0]!=1)
            throw new IOException("Guest NACKed PBW PutBytes");
        return (int)(((long)(ack.payload[1]&255)<<24)
                |((long)(ack.payload[2]&255)<<16)
                |((long)(ack.payload[3]&255)<<8)
                |(ack.payload[4]&255));
    }
    private static void writeBe32(ByteArrayOutputStream output,long n){
        output.write((int)(n>>24)&255);
        output.write((int)(n>>16)&255);
        output.write((int)(n>>8)&255);
        output.write((int)n&255);
    }
    private static byte[] uuidBytes(UUID uuid){
        ByteArrayOutputStream b=new ByteArrayOutputStream(16);
        EmeryPebbleWire.uuid(b,uuid);return b.toByteArray();
    }

    private static JSONObject readManifest(ZipFile zip,String name)throws IOException,JSONException {
        byte[] data=read(zip,"",name);
        if(data.length>256*1024)throw new IOException("PBW manifest too large");
        return new JSONObject(new String(data,StandardCharsets.UTF_8));
    }
    private static byte[] read(ZipFile zip,String prefix,String path)throws IOException {
        if(path==null||path.isEmpty()||path.startsWith("/")||
                path.contains("..")||path.contains("\\"))
            throw new IOException("Unsafe PBW bundle path");
        ZipEntry entry=zip.getEntry(prefix+path);
        if(entry==null||entry.isDirectory()||entry.getSize()<0||
                entry.getSize()>MAX_BINARY)
            throw new IOException("Invalid PBW bundle file: "+prefix+path);
        try(InputStream in=zip.getInputStream(entry);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>MAX_BINARY)
                    throw new IOException("PBW bundle file expands beyond 20 MB");
                out.write(buffer,0,n);
            }
            return out.toByteArray();
        }
    }
}
