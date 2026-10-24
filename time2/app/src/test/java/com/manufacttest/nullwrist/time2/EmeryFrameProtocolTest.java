package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

public class EmeryFrameProtocolTest {
    private static File frame(int sequence,int format,int width) throws IOException {
        File file=File.createTempFile("emery-fb-", ".bin");
        file.deleteOnExit();
        byte[] all=new byte[EmeryFrameProtocol.TOTAL_BYTES];
        ByteBuffer b=ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(EmeryFrameProtocol.MAGIC);
        b.putInt(EmeryFrameProtocol.VERSION);
        b.putInt(width);
        b.putInt(EmeryFrameProtocol.HEIGHT);
        b.putInt(width);
        b.putInt(format);
        b.putInt(sequence);
        b.position(EmeryFrameProtocol.HEADER_BYTES);
        b.putInt(0xFFFF6D00);
        b.putInt(0xFF08090D);
        try(FileOutputStream out=new FileOutputStream(file)){out.write(all);}
        return file;
    }

    @Test public void decodesCompleteFrame() throws Exception {
        EmeryFrameProtocol.Frame result=EmeryFrameProtocol.read(
                frame(2,EmeryFrameProtocol.FORMAT_ARGB8888,200));
        assertNotNull(result);
        assertEquals(2,result.sequence);
        assertEquals(200*228,result.argbPixels.length);
        assertEquals(0xFFFF6D00,result.argbPixels[0]);
        assertEquals(0xFF08090D,result.argbPixels[1]);
    }
    @Test public void rejectsTornAndUnknownFrames() throws Exception {
        assertNull(EmeryFrameProtocol.read(frame(1,2,200)));
        assertNull(EmeryFrameProtocol.read(frame(0,2,200)));
        assertNull(EmeryFrameProtocol.read(frame(2,1,200)));
        assertNull(EmeryFrameProtocol.read(frame(2,2,144)));
    }
    @Test public void handlesUncreatedOrTruncatedFiles() throws Exception {
        File f=File.createTempFile("emery-fb-", ".bin");
        f.deleteOnExit();
        assertNull(EmeryFrameProtocol.read(f));
        assertTrue(f.delete());
        assertNull(EmeryFrameProtocol.read(f));
    }
}
