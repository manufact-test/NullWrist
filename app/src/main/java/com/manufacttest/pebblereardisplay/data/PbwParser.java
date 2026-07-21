package com.manufacttest.pebblereardisplay.data;

import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PbwParser {
    private static final int MAX_APPINFO_BYTES = 1024 * 1024;

    private PbwParser() {}

    public static WatchfaceMetadata parse(
            InputStream inputStream,
            String storageId,
            boolean bundled
    ) throws IOException {
        byte[] appInfoBytes = null;
        boolean hasPhoneJavaScript = false;

        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if ("appinfo.json".equals(entryName)) {
                    appInfoBytes = readLimited(zip, MAX_APPINFO_BYTES);
                } else if ("pebble-js-app.js".equals(entryName)) {
                    hasPhoneJavaScript = true;
                }
            }
        }

        if (appInfoBytes == null) {
            throw new IOException("PBW does not contain appinfo.json");
        }

        try {
            JSONObject root = new JSONObject(new String(appInfoBytes, StandardCharsets.UTF_8));
            JSONObject watchApp = root.optJSONObject("watchapp");
            if (watchApp == null || !watchApp.optBoolean("watchface", false)) {
                throw new IOException("PBW is not marked as a watchface");
            }

            String name = firstNonBlank(
                    root.optString("longName"),
                    root.optString("shortName"),
                    root.optString("displayName"),
                    root.optString("name"),
                    "Unknown watchface"
            );

            return new WatchfaceMetadata(
                    storageId,
                    name,
                    firstNonBlank(root.optString("companyName"), "Unknown author"),
                    firstNonBlank(root.optString("versionLabel"), "Unknown version"),
                    firstNonBlank(root.optString("uuid"), "Unknown UUID"),
                    jsonArrayToStrings(root.optJSONArray("targetPlatforms")),
                    jsonArrayToStrings(root.optJSONArray("capabilities")),
                    hasPhoneJavaScript,
                    bundled
            );
        } catch (JSONException exception) {
            throw new IOException("Invalid appinfo.json", exception);
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("appinfo.json is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static List<String> jsonArrayToStrings(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
