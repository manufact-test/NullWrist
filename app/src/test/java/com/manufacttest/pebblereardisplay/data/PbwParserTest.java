package com.manufacttest.pebblereardisplay.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PbwParserTest {
    @Test
    public void parsesWatchfaceMetadataAndPhoneJavaScript() throws Exception {
        String appInfo = "{"
                + "\"longName\":\"Test Face\","
                + "\"companyName\":\"Example Author\","
                + "\"versionLabel\":\"1.2.3\","
                + "\"uuid\":\"00000000-0000-0000-0000-000000000001\","
                + "\"targetPlatforms\":[\"basalt\",\"chalk\"],"
                + "\"watchapp\":{\"watchface\":true}"
                + "}";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("appinfo.json"));
            zip.write(appInfo.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pebble-js-app.js"));
            zip.write("Pebble.addEventListener('ready', function() {});"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        WatchfaceMetadata result = PbwParser.parse(
                new ByteArrayInputStream(bytes.toByteArray()),
                "test.pbw",
                false
        );

        assertEquals("Test Face", result.getName());
        assertEquals("Example Author", result.getAuthor());
        assertEquals(2, result.getPlatforms().size());
        assertTrue(result.hasPhoneJavaScript());
    }
}
