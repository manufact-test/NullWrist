package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Validates copy-before-write, no reset, and durable recovery snapshots. */
public class EmerySpiGuardianTest {
    @Test public void preservesTwoIndependentCopiesWithoutReplacingUserFlash()throws Exception{
        File dir=Files.createTempDirectory("emery-flash-checkpoint").toFile();
        File spi=new File(dir,"qemu_spi_flash.bin");
        try {
            try(RandomAccessFile f=new RandomAccessFile(spi,"rw")){
                f.setLength(EmerySpiGuardian.SPI_SIZE);
                f.seek(16384);f.write(101);
            }
            EmerySpiGuardian guardian=new EmerySpiGuardian(spi);
            String initial=EmerySpiGuardian.digest(spi);
            guardian.beforeBoot();
            File pre=new File(dir,"spi-checkpoints/before-boot.bin");
            assertTrue(pre.isFile());
            assertEquals(EmerySpiGuardian.SPI_SIZE,pre.length());
            assertEquals(initial,EmerySpiGuardian.digest(pre));
            try(RandomAccessFile f=new RandomAccessFile(spi,"rw")){
                f.seek(16384);f.write(102);
            }
            guardian.afterCleanStop();
            File post=new File(dir,"spi-checkpoints/after-clean-stop.bin");
            assertTrue(post.isFile());
            assertNotEquals(EmerySpiGuardian.digest(pre),EmerySpiGuardian.digest(post));
            assertEquals(EmerySpiGuardian.digest(spi),EmerySpiGuardian.digest(post));
            assertEquals(initial,EmerySpiGuardian.digest(pre));
        }finally{
            File cp=new File(dir,"spi-checkpoints");
            for(File x:new File[]{new File(cp,"before-boot.bin"),
                    new File(cp,"after-clean-stop.bin"),spi})x.delete();
            cp.delete();dir.delete();
        }
    }
    @Test public void rejectsTruncatedFlashWithoutReplacingOrFormattingIt()throws Exception{
        File dir=Files.createTempDirectory("emery-bad-flash").toFile();
        File spi=new File(dir,"qemu_spi_flash.bin");
        try{
            Files.write(spi.toPath(),new byte[]{1,2,3});
            try{new EmerySpiGuardian(spi).beforeBoot();fail("must stop");}
            catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("length"));}
            assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(spi.toPath()));
            assertFalse(new File(dir,"spi-checkpoints/before-boot.bin").exists());
        }finally{spi.delete();dir.delete();}
    }
}
