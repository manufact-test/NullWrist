package com.manufacttest.pebblereardisplay.runtime;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads the Basalt-compatible executable, resources and metadata from a PBW archive. */
public final class PebblePbwBundle implements Closeable {
    private static final int APP_HEADER_BYTES = 120;
    private static final int MAX_PART_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private final ZipFile zip;
    private final AppHeader header;
    private final byte[] application;
    private final byte[] resources;
    private final byte[] worker;

    public PebblePbwBundle(File file) throws IOException {
        zip = new ZipFile(file);
        try {
            String manifestPath = chooseManifestPath();
            String prefix = manifestPath.substring(0, manifestPath.length() - "manifest.json".length());
            JSONObject manifest = readJson(manifestPath);
            JSONObject applicationInfo = manifest.optJSONObject("application");
            if (applicationInfo == null) {
                throw new IOException("PBW manifest does not contain an application");
            }

            application = readRequiredPart(prefix, applicationInfo, "application");
            header = AppHeader.parse(application);
            resources = readOptionalPart(prefix, manifest.optJSONObject("resources"), "resources");
            worker = readOptionalPart(prefix, manifest.optJSONObject("worker"), "worker");
        } catch (Throwable error) {
            try {
                zip.close();
            } catch (IOException ignored) {
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Cannot parse PBW", error);
        }
    }

    public AppHeader getHeader() {
        return header;
    }

    public byte[] getApplication() {
        return application;
    }

    public byte[] getResources() {
        return resources;
    }

    public byte[] getWorker() {
        return worker;
    }

    public int getTotalTransferBytes() {
        return application.length
                + (resources == null ? 0 : resources.length)
                + (worker == null ? 0 : worker.length);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    private String chooseManifestPath() throws IOException {
        if (zip.getEntry("basalt/manifest.json") != null) {
            return "basalt/manifest.json";
        }
        if (zip.getEntry("manifest.json") != null) {
            return "manifest.json";
        }
        throw new IOException("PBW has no Basalt-compatible manifest");
    }

    private JSONObject readJson(String path) throws IOException {
        byte[] bytes = readEntry(path, MAX_MANIFEST_BYTES);
        try {
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException("Invalid " + path, error);
        }
    }

    private byte[] readRequiredPart(String prefix, JSONObject info, String label) throws IOException {
        String name = info.optString("name", "").trim();
        if (name.isEmpty()) {
            throw new IOException("PBW manifest has no " + label + " filename");
        }
        return readEntry(prefix + name, MAX_PART_BYTES);
    }

    private byte[] readOptionalPart(String prefix, JSONObject info, String label) throws IOException {
        if (info == null) {
            return null;
        }
        return readRequiredPart(prefix, info, label);
    }

    private byte[] readEntry(String path, int limit) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("PBW is missing " + path);
        }
        long declaredSize = entry.getSize();
        if (declaredSize > limit) {
            throw new IOException(path + " is too large");
        }

        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     declaredSize > 0 ? (int) declaredSize : 8192
             )) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException(path + " is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    public static final class AppHeader {
        private final int sdkVersionMajor;
        private final int sdkVersionMinor;
        private final int appVersionMajor;
        private final int appVersionMinor;
        private final int iconResourceId;
        private final int flags;
        private final String appName;
        private final UUID uuid;

        private AppHeader(
                int sdkVersionMajor,
                int sdkVersionMinor,
                int appVersionMajor,
                int appVersionMinor,
                int iconResourceId,
                int flags,
                String appName,
                UUID uuid
        ) {
            this.sdkVersionMajor = sdkVersionMajor;
            this.sdkVersionMinor = sdkVersionMinor;
            this.appVersionMajor = appVersionMajor;
            this.appVersionMinor = appVersionMinor;
            this.iconResourceId = iconResourceId;
            this.flags = flags;
            this.appName = appName;
            this.uuid = uuid;
        }

        public int getSdkVersionMajor() {
            return sdkVersionMajor;
        }

        public int getSdkVersionMinor() {
            return sdkVersionMinor;
        }

        public int getAppVersionMajor() {
            return appVersionMajor;
        }

        public int getAppVersionMinor() {
            return appVersionMinor;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        public int getFlags() {
            return flags;
        }

        public String getAppName() {
            return appName;
        }

        public UUID getUuid() {
            return uuid;
        }

        private static AppHeader parse(byte[] binary) throws IOException {
            if (binary.length < APP_HEADER_BYTES) {
                throw new IOException("Pebble executable is shorter than its app header");
            }
            ByteBuffer data = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
            data.position(8);
            data.get(); // struct version major
            data.get(); // struct version minor
            int sdkMajor = data.get() & 0xff;
            int sdkMinor = data.get() & 0xff;
            int appMajor = data.get() & 0xff;
            int appMinor = data.get() & 0xff;
            data.getShort(); // app size
            data.getInt(); // load offset
            data.getInt(); // executable CRC

            byte[] appNameBytes = new byte[32];
            data.get(appNameBytes);
            data.position(data.position() + 32); // company name
            int icon = data.getInt();
            data.getInt(); // symbol table address
            int flags = data.getInt();
            data.getInt(); // relocation count
            byte[] uuidBytes = new byte[16];
            data.get(uuidBytes);

            ByteBuffer uuidBuffer = ByteBuffer.wrap(uuidBytes).order(ByteOrder.BIG_ENDIAN);
            UUID uuid = new UUID(uuidBuffer.getLong(), uuidBuffer.getLong());
            return new AppHeader(
                    sdkMajor,
                    sdkMinor,
                    appMajor,
                    appMinor,
                    icon,
                    flags,
                    decodeFixedString(appNameBytes),
                    uuid
            );
        }

        private static String decodeFixedString(byte[] value) {
            int length = 0;
            while (length < value.length && value[length] != 0) {
                length++;
            }
            return new String(value, 0, length, StandardCharsets.UTF_8);
        }
    }
}
