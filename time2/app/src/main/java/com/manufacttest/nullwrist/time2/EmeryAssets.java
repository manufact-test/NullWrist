package com.manufacttest.nullwrist.time2;

import android.content.Context;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Verified, isolated, opt-in Emery firmware staging. The imported SPI is
 * writable/persistent; the guest ELF and QEMU binary are immutable and
 * checked against CI SHA-256 manifests on each startup.
 */
public final class EmeryAssets {
    public static final String ELF = "pebbleos.elf";
    public static final String SPI = "qemu_spi_flash.bin";
    public static final String NATIVE = "libnullwrist_emery_qemu.so";
    private static final String ROOT = "emery/";
    private static final long MAX_ASSET_BYTES = 25L * 1024 * 1024;
    private static final long MAX_NATIVE_BYTES = 128L * 1024 * 1024;

    private EmeryAssets() {}

    public static boolean isBundled(Context context) {
        try (InputStream elf = context.getAssets().open(ROOT + ELF);
             InputStream spi = context.getAssets().open(ROOT + SPI);
             InputStream hashes = context.getAssets().open(ROOT + "SHA256SUMS");
             InputStream nativeHash = context.getAssets().open(ROOT + "native.sha256")) {
            File nativeFile = new File(context.getApplicationInfo().nativeLibraryDir,NATIVE);
            return nativeFile.isFile() && nativeFile.canExecute()
                    && elf.read() != -1 && spi.read() != -1
                    && hashes.read() != -1 && nativeHash.read() != -1;
        } catch (IOException error) {
            return false;
        }
    }

    public static Staged stage(Context context) throws IOException {
        if (!isBundled(context)) throw new IOException("Emery native/firmware artifacts are not bundled");
        File privateRoot = new File(context.getFilesDir(), "time2-emery");
        if (!privateRoot.isDirectory() && !privateRoot.mkdirs())
            throw new IOException("Cannot create emulator private directory");
        Map<String,String> expected=readManifest(context,ROOT+"SHA256SUMS");
        Map<String,String> qemuHash=readManifest(context,ROOT+"native.sha256");

        File qemu=new File(context.getApplicationInfo().nativeLibraryDir,NATIVE);
        validateHash(qemu,requiredHash(qemuHash,NATIVE),MAX_NATIVE_BYTES);

        File elf=new File(privateRoot,ELF);
        if (!elf.isFile() || !hash(elf).equals(requiredHash(expected,ELF))) {
            atomicInstall(context,ROOT+ELF,elf,requiredHash(expected,ELF));
        }
        File spi=new File(privateRoot,SPI);
        if (!spi.isFile()) atomicInstall(context,ROOT+SPI,spi,requiredHash(expected,SPI));
        if (!spi.isFile() || spi.length() <= 0 || spi.length() > MAX_ASSET_BYTES)
            throw new IOException("Invalid writable SPI storage");

        return new Staged(privateRoot,qemu,elf,spi,
                new File(privateRoot,"emery-fb-v2.bin"),
                new File(privateRoot,"emery-qmp.sock"),
                new File(privateRoot,"emery-qemu.log"));
    }

    private static Map<String,String> readManifest(Context c,String path) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try (InputStream stream=c.getAssets().open(path)) {
            byte[] chunk=new byte[1024];
            int n;
            while((n=stream.read(chunk))!=-1){
                if(output.size()+n>4096) throw new IOException("Manifest too large");
                output.write(chunk,0,n);
            }
        }
        if(output.size()==0)throw new IOException("Empty artifact hash manifest");
        String manifest=new String(output.toByteArray(),StandardCharsets.UTF_8);
        Map<String,String> result=new HashMap<>();
        for(String raw:manifest.split("\n")){
            String line=raw.trim();
            if(line.isEmpty()) continue;
            String[] fields=line.split("\\s+",2);
            if(fields.length!=2 || !fields[0].matches("(?i)[a-f0-9]{64}"))
                throw new IOException("Malformed artifact hash manifest");
            String name=fields[1].trim().replaceFirst("^[* ]+","");
            if(name.contains("/") || name.contains("\\") || result.put(name,
                    fields[0].toLowerCase(Locale.ROOT))!=null)
                throw new IOException("Invalid artifact filename in manifest");
        }
        return result;
    }

    private static String requiredHash(Map<String,String> hashes,String name) throws IOException {
        String hash=hashes.get(name);
        if(hash==null) throw new IOException("Manifest lacks "+name);
        return hash;
    }

    private static String hash(File file) throws IOException {
        try(FileInputStream stream=new FileInputStream(file)){
            return hex(digest(stream,file.getName().equals(NATIVE) ? MAX_NATIVE_BYTES : MAX_ASSET_BYTES));
        }
    }
    private static void validateHash(File file,String sha,long maxSize) throws IOException {
        if(!file.isFile() || file.length()<1 || file.length()>maxSize
                || !hash(file).equals(sha))
            throw new IOException("Emery artifact SHA-256 mismatch: "+file.getName());
    }
    private static byte[] digest(InputStream stream,long maxSize) throws IOException {
        final MessageDigest md;
        try { md=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}
        byte[] buffer=new byte[8192];
        long total=0;
        int n;
        while((n=stream.read(buffer))!=-1){
            total+=n;
            if(total>maxSize) throw new IOException("Emery asset too large");
            md.update(buffer,0,n);
        }
        return md.digest();
    }
    private static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder(bytes.length*2);
        for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b & 255));
        return result.toString();
    }
    private static void atomicInstall(Context context,String assetPath,File output,String expected)
            throws IOException {
        File tmp=new File(output.getParentFile(),output.getName()+".tmp");
        if(tmp.exists() && !tmp.delete()) throw new IOException("Cannot clear staging file");
        try {
            try (InputStream input=context.getAssets().open(assetPath);
                 FileOutputStream out=new FileOutputStream(tmp)) {
                byte[] buffer=new byte[8192]; int n; long size=0;
                while((n=input.read(buffer))!=-1){
                    size+=n;
                    if(size>MAX_ASSET_BYTES) throw new IOException("Firmware asset too large");
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
            }
            validateHash(tmp,expected,MAX_ASSET_BYTES);
            if(output.exists() && !output.delete()) throw new IOException("Cannot replace corrupt immutable firmware");
            if(!tmp.renameTo(output)) throw new IOException("Cannot install emulator asset atomically");
        } finally {
            if(tmp.exists()) tmp.delete();
        }
    }

    public static final class Staged {
        public final File root;
        public final File qemu;
        public final File elf;
        public final File spi;
        public final File frame;
        public final File qmp;
        public final File log;
        Staged(File root,File qemu,File elf,File spi,File frame,File qmp,File log){
            this.root=root;this.qemu=qemu;this.elf=elf;this.spi=spi;
            this.frame=frame;this.qmp=qmp;this.log=log;
        }
    }
}
