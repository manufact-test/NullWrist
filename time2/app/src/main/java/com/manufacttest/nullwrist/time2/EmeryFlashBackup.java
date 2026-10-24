package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * User-owned backup/restore of the entire private Emery SPI image.
 *
 * The official emulator stores installed apps, settings and filesystem on
 * qemu_spi_flash.bin. Never write it while QEMU owns it. We use a staging
 * file + atomic replacement, so a truncated user-provided backup cannot
 * destroy the previous working image.
 */
public final class EmeryFlashBackup {
    private EmeryFlashBackup() {}

    public static void exportImage(Context context,Uri destination)throws IOException{
        if(EmeryRuntime.isDeviceLabActive())
            throw new IOException("Close the Emery screen before backing up its flash");
        File source=EmeryAssets.stage(context).spi;
        long originalLength=source.length();
        if(originalLength==0)throw new IOException("Flash image is empty");
        try(InputStream in=new FileInputStream(source);
            OutputStream out=context.getContentResolver().openOutputStream(destination,"w")){
            if(out==null)throw new IOException("Cannot create backup destination");
            byte[] buffer=new byte[65536];
            long copied=0;
            int n;
            while((n=in.read(buffer))!=-1){
                if(EmeryRuntime.isDeviceLabActive())
                    throw new IOException("Emery session started during flash backup");
                copied+=n;
                if(copied>originalLength)throw new IOException("Flash was modified during backup");
                out.write(buffer,0,n);
            }
            out.flush();
            if(copied!=originalLength)throw new IOException("Partial backup");
        }
    }

    public static void restoreImage(Context context,Uri source)throws IOException{
        if(EmeryRuntime.isDeviceLabActive())
            throw new IOException("Close Emery before restoring its memory");
        File destination=EmeryAssets.stage(context).spi;
        long expected=destination.length();
        if(expected<=0 || expected>64L*1024*1024)
            throw new IOException("No valid image size established");
        File temporary=new File(destination.getParentFile(),"spi-restore.tmp");
        if(temporary.exists()&&!temporary.delete())
            throw new IOException("Cannot clear stale restore staging file");
        try{
            try(InputStream in=context.getContentResolver().openInputStream(source);
                FileOutputStream out=new FileOutputStream(temporary)){
                if(in==null)throw new IOException("Cannot read selected memory backup");
                byte[] buffer=new byte[65536];
                long copied=0;
                int n;
                while((n=in.read(buffer))!=-1){
                    copied+=n;
                    if(copied>expected)throw new IOException("Backup is larger than the Emery flash");
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
                if(copied!=expected)
                    throw new IOException(String.format(Locale.ROOT,
                            "Incorrect backup size (%d bytes instead of %d)",copied,expected));
            }
            if(EmeryRuntime.isDeviceLabActive())
                throw new IOException("Emery session started during restore; no changes applied");
            try{
                Files.move(temporary.toPath(),destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }catch(java.nio.file.AtomicMoveNotSupportedException unsupported){
                throw new IOException("Atomic flash restore unsupported; preserving old data",
                        unsupported);
            }
        }finally{
            if(temporary.exists())temporary.delete();
        }
    }
}
