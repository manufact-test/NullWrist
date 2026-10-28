package com.manufacttest.nullwrist.time2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Defense in depth: a recoverable 32MiB snapshot BEFORE QEMU boots and
 * an independent snapshot AFTER a protocol shutdown. Never restore or reset
 * the user's SPI implicitly. Snapshots live inside Time2 private storage.
 */
final class EmerySpiGuardian {
    static final long SPI_SIZE=32L*1024*1024;
    private final File image;
    private final File directory;
    private final File beforeBoot;
    private final File cleanStop;

    EmerySpiGuardian(File image) {
        this.image=image;
        this.directory=new File(image.getParentFile(),"spi-checkpoints");
        this.beforeBoot=new File(directory,"before-boot.bin");
        this.cleanStop=new File(directory,"after-clean-stop.bin");
    }

    static String digest(File source)throws IOException {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            try(FileInputStream input=new FileInputStream(source)){
                byte[] buffer=new byte[65536];int n;
                while((n=input.read(buffer))!=-1)md.update(buffer,0,n);
            }
            StringBuilder text=new StringBuilder(64);
            for(byte b:md.digest())text.append(String.format(Locale.ROOT,"%02x",b&255));
            return text.toString();
        }catch(NoSuchAlgorithmException bad){throw new IOException(bad);}
    }

    private void requireValid()throws IOException{
        if(!image.isFile()||image.length()!=SPI_SIZE)
            throw new IOException("SPI image missing or changed length; data preserved, emulator not started");
    }
    void beforeBoot()throws IOException{
        requireValid();
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Cannot create SPI checkpoint directory");
        copyAtomic(image,beforeBoot);
    }
    void afterCleanStop()throws IOException{
        requireValid();
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Cannot create SPI checkpoint directory");
        copyAtomic(image,cleanStop);
    }
    private static void copyAtomic(File input,File dest)throws IOException{
        File temp=new File(dest.getParentFile(),dest.getName()+".tmp");
        if(temp.exists()&&!temp.delete())throw new IOException("Stale SPI temporary copy");
        try{
            try(FileInputStream in=new FileInputStream(input);
                FileOutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[65536];
                long total=0;int n;
                while((n=in.read(buffer))!=-1){
                    out.write(buffer,0,n);
                    total+=n;
                    if(total>SPI_SIZE)throw new IOException("Oversized SPI backup");
                }
                out.getFD().sync();
                if(total!=SPI_SIZE)throw new IOException("Truncated SPI backup");
            }
            try{
                Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }catch(java.nio.file.AtomicMoveNotSupportedException e){
                throw new IOException("Atomic checkpoints unavailable; user SPI unchanged",e);
            }
        }finally{if(temp.exists())temp.delete();}
    }
}
