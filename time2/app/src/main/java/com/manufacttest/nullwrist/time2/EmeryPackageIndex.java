package com.manufacttest.nullwrist.time2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only PBW metadata scanner, never extracts entries or runs PBW code. */
public final class EmeryPackageIndex {
    public static final long MAX_PBW_BYTES = 20L * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private EmeryPackageIndex() {}

    public static PackageInfo inspect(File file) throws IOException {
        if (!file.isFile() || file.length() < 1 || file.length() > MAX_PBW_BYTES)
            throw new IOException("PBW file size must be 1 byte to 20 MB");
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("appinfo.json");
            if (entry == null || entry.isDirectory() || entry.getSize() > MAX_METADATA_BYTES)
                throw new IOException("Missing or oversized appinfo.json");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = zip.getInputStream(entry)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (output.size() + n > MAX_METADATA_BYTES)
                        throw new IOException("PBW metadata exceeds 1 MB");
                    output.write(buffer,0,n);
                }
            }
            JSONObject json = new JSONObject(output.toString(StandardCharsets.UTF_8));
            JSONArray targets = json.optJSONArray("targetPlatforms");
            boolean supportsEmery = false;
            if (targets != null) {
                for (int i=0; i<targets.length(); i++)
                    if ("emery".equalsIgnoreCase(targets.optString(i))) supportsEmery = true;
            }
            JSONObject watchApp = json.optJSONObject("watchapp");
            boolean face = watchApp != null && watchApp.optBoolean("watchface",false);
            String uuidString = json.optString("uuid","");
            UUID uuid;
            try { uuid = UUID.fromString(uuidString); }
            catch (IllegalArgumentException e) { throw new IOException("PBW UUID is invalid", e); }
            if (!supportsEmery || zip.getEntry("emery/manifest.json") == null)
                throw new IOException("PBW does not have an Emery executable / manifest");
            String name = json.optString("longName",json.optString("shortName","Pebble app"));
            String author = json.optString("companyName","Unknown developer");
            return new PackageInfo(uuid.toString(),name,author,face);
        } catch (JSONException error) {
            throw new IOException("Invalid PBW appinfo.json",error);
        }
    }

    public static final class PackageInfo {
        public final String uuid;
        public final String name;
        public final String author;
        public final boolean watchface;
        public PackageInfo(String uuid,String name,String author,boolean watchface) {
            this.uuid=uuid; this.name=name; this.author=author; this.watchface=watchface;
        }
    }
}
