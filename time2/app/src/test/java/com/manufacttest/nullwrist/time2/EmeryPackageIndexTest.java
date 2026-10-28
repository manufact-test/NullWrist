package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class EmeryPackageIndexTest {
    private final UUID id=UUID.fromString("bce948c0-2d3f-4f55-a1a5-81234567abcd");

    private File bundle(String platform,int sdkMinor,boolean mismatch)throws Exception{
        File temp=File.createTempFile("emery-bundle",".pbw");
        temp.deleteOnExit();
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temp))){
            add(zip,"appinfo.json",("{\"uuid\":\""+id+"\",\"shortName\":\"Test PBW\",\"companyName\":\"NullWrist\",\"watchapp\":{\"watchface\":true}}").getBytes(StandardCharsets.UTF_8));
            add(zip,platform+"manifest.json", "{\"application\":{\"name\":\"pebble-app.bin\"}}".getBytes(StandardCharsets.UTF_8));
            byte[] binary=new byte[160];
            binary[11]=(byte)sdkMinor;
            UUID metadataUuid=mismatch?UUID.randomUUID():id;
            ByteBuffer header=ByteBuffer.wrap(binary);
            header.position(104);
            header.putLong(metadataUuid.getMostSignificantBits());
            header.putLong(metadataUuid.getLeastSignificantBits());
            add(zip,platform+"pebble-app.bin",binary);
        }
        return temp;
    }
    private static void add(ZipOutputStream zip,String name,byte[] data)throws Exception{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
    @Test public void directEmeryIsSupported() throws Exception {
        EmeryPackageIndex.PackageInfo pkg=EmeryPackageIndex.inspect(bundle("emery/",0x89,false));
        assertEquals("emery",pkg.platform);
        assertEquals("Test PBW",pkg.name);
        assertTrue(pkg.watchface);
    }
    @Test public void basaltCompatibilitySdkCap() throws Exception {
        assertEquals("basalt",EmeryPackageIndex.inspect(bundle("basalt/",0x53,false)).platform);
        try{EmeryPackageIndex.inspect(bundle("basalt/",0x54,false));fail("SDK cap ignored");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("No supported"));}
    }
    @Test public void originalRootFormatIsSupportedWithinCap() throws Exception {
        assertEquals("legacy",EmeryPackageIndex.inspect(bundle("",0x15,false)).platform);
        try{EmeryPackageIndex.inspect(bundle("",0x16,false));fail("SDK cap ignored");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("No supported"));}
    }
    @Test public void spoofedMetadataUuidIsRejected() throws Exception {
        try{EmeryPackageIndex.inspect(bundle("emery/",0x02,true));fail("UUID spoof accepted");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("UUID differs"));}
    }
}
