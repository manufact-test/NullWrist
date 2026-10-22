package com.manufacttest.nullwrist.time2;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PBW metadata/binary validation against official Core Devices emery fallback.
 * Does not execute apps; all files stay in private user-imported storage.
 *
 * coredevices/libpebble2 util/bundle.py: emery -> basalt -> root fallback.
 * Basalt SDK minor MUST be < 0x54; root SDK minor MUST be < 0x16.
 * These are upstream platform compatibility limits, not user assumptions.
 */
public final class EmeryPackageIndex {
    public static final long MAX_PBW_BYTES=20L*1024*1024;
    private static final int MAX_METADATA_BYTES=256*1024;
    private static final long MAX_EXPANDED_BYTES=64L*1024*1024;
    private static final int MAX_ENTRIES=256;
    private static final int BINARY_METADATA_BYTES=120;
    private EmeryPackageIndex() {}

    public static PackageInfo inspect(File file) throws IOException {
        if(!file.isFile()||file.length()==0||file.length()>MAX_PBW_BYTES)
            throw new IOException("PBW must be 1 byte to 20 MB");
        try(ZipFile zip=new ZipFile(file)){
            if(zip.size()>MAX_ENTRIES)throw new IOException("Too many PBW entries");
            long sum=0;
            java.util.Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();
                long size=e.getSize();
                if(size<0)throw new IOException("PBW entry has unknown size");
                sum+=size;
                if(sum>MAX_EXPANDED_BYTES)throw new IOException("PBW expands beyond 64 MB");
            }
            JSONObject info=readJson(zip,"appinfo.json");
            UUID uuid;
            try{uuid=UUID.fromString(info.optString("uuid",""));}
            catch(IllegalArgumentException e){throw new IOException("Invalid PBW UUID",e);}
            String[] options={"emery/","basalt/",""};
            int[] sdkCaps={256,0x54,0x16};
            for(int i=0;i<options.length;i++){
                String prefix=options[i];
                String manifestPath=prefix+"manifest.json";
                if(zip.getEntry(manifestPath)==null)continue;
                JSONObject manifest=readJson(zip,manifestPath);
                JSONObject application=manifest.optJSONObject("application");
                if(application==null)continue;
                String relative=application.optString("name","");
                // Bundle manifest filename must stay inside chosen platform directory.
                if(relative.isEmpty()||relative.startsWith("/")||relative.startsWith(".")
                        ||relative.contains("..")||relative.contains("\\"))
                    throw new IOException("Invalid PBW executable path");
                String executable=prefix+relative;
                ZipEntry executableEntry=zip.getEntry(executable);
                if(executableEntry==null||executableEntry.isDirectory()
                        ||executableEntry.getSize()<BINARY_METADATA_BYTES)
                    throw new IOException("PBW executable missing: "+executable);
                byte[] binaryHeader=new byte[BINARY_METADATA_BYTES];
                try(InputStream stream=zip.getInputStream(executableEntry)){
                    int filled=0;
                    while(filled<binaryHeader.length){
                        int n=stream.read(binaryHeader,filled,binaryHeader.length-filled);
                        if(n<0)throw new IOException("Truncated Pebble app header");
                        filled+=n;
                    }
                }
                int sdkMinor=binaryHeader[11]&0xff;
                if(sdkMinor>=sdkCaps[i])continue; // unsupported compatibility ABI
                ByteBuffer header=ByteBuffer.wrap(binaryHeader);
                header.position(104); // 8s,6B,H,2I,32s,32s,4I, then UUID
                UUID binaryUuid=new UUID(header.getLong(),header.getLong());
                if(!binaryUuid.equals(uuid))
                    throw new IOException("PBW appinfo UUID differs from executable");
                JSONObject watchapp=info.optJSONObject("watchapp");
                boolean watchface=watchapp!=null&&watchapp.optBoolean("watchface",false);
                String platform=prefix.equals("")?"legacy":prefix.substring(0,prefix.length()-1);
                return new PackageInfo(uuid.toString(),trim(info.optString("longName",
                        info.optString("shortName","Pebble app"))),trim(info.optString(
                        "companyName","Unknown")),watchface,platform);
            }
            throw new IOException("No supported Emery/Basalt-compatible PBW binary found");
        }catch(JSONException e){throw new IOException("Invalid PBW JSON metadata",e);}
    }

    private static String trim(String source){
        String normalized=source.trim().replace('\n',' ').replace('\r',' ');
        return normalized.length()>100?normalized.substring(0,100):normalized;
    }

    private static JSONObject readJson(ZipFile zip,String path)throws IOException,JSONException {
        ZipEntry entry=zip.getEntry(path);
        if(entry==null||entry.isDirectory()||entry.getSize()<0
                ||entry.getSize()>MAX_METADATA_BYTES)
            throw new IOException("Missing or oversized PBW metadata: "+path);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream stream=zip.getInputStream(entry)){
            byte[] buffer=new byte[8192];
            int n;
            while((n=stream.read(buffer))!=-1){
                if(output.size()+n>MAX_METADATA_BYTES)
                    throw new IOException("PBW metadata exceeds 256 KB");
                output.write(buffer,0,n);
            }
        }
        return new JSONObject(output.toString(StandardCharsets.UTF_8));
    }

    public static final class PackageInfo {
        public final String uuid,name,author,platform;
        public final boolean watchface;
        public PackageInfo(String uuid,String name,String author,boolean watchface,String platform){
            this.uuid=uuid;this.name=name;this.author=author;
            this.watchface=watchface;this.platform=platform;
        }
    }
}
