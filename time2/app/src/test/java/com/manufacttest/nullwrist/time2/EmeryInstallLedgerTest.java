package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class EmeryInstallLedgerTest {
    @Test public void shaChecksOriginalFileAndDetectsChangedBytes()throws Exception{
        File f=File.createTempFile("time2-ledger-",".pbw");
        try{
            byte[] a=new byte[]{1,2,3,4,5,6};
            Files.write(f.toPath(),a);
            String first=EmeryInstallLedger.sha256(f);
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] expected=md.digest(a);
            StringBuilder hex=new StringBuilder();
            for(byte b:expected)hex.append(String.format("%02x",b&255));
            assertEquals(hex.toString(),first);
            Files.write(f.toPath(),new byte[]{1,2,3,4,5,7});
            assertNotEquals(first,EmeryInstallLedger.sha256(f));
        }finally{f.delete();}
    }
    @Test public void refusesMissingPbws()throws Exception{
        File f=File.createTempFile("time2-ledger-",".pbw");
        f.delete();
        try{EmeryInstallLedger.sha256(f);fail("missing should be refused");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("Missing"));}
    }
}
