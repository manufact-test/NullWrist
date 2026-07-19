package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Mirrors the apps persisted in the emulator SPI flash using PBW SHA-256 fingerprints. */
public final class InstalledWatchfaceRegistry {
    private static final String PREFS = "installed_watchfaces_v1";
    private static final String PREFIX = "watchface_";
    private static final String PRESEED_MANIFEST = "pebble/basalt/preseeded-watchfaces.json";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private final Context context;
    private final SharedPreferences preferences;

    public InstalledWatchfaceRegistry(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isInstalled(UUID uuid, String sha256) {
        return sha256 != null && sha256.equals(preferences.getString(key(uuid), null));
    }

    public void markInstalled(UUID uuid, String sha256) {
        preferences.edit().putString(key(uuid), sha256).apply();
    }

    public void forget(UUID uuid) {
        preferences.edit().remove(key(uuid)).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    /** Replaces the registry with the exact UUID/SHA set baked into a fresh SPI image. */
    public void seedFromBundledFlash() throws IOException {
        SharedPreferences.Editor editor = preferences.edit().clear();
        try (InputStream input = context.getAssets().open(PRESEED_MANIFEST)) {
            JSONObject root = new JSONObject(readLimited(input));
            JSONArray watchfaces = root.getJSONArray("watchfaces");
            for (int index = 0; index < watchfaces.length(); index++) {
                JSONObject item = watchfaces.getJSONObject(index);
                UUID uuid = UUID.fromString(item.getString("uuid"));
                String sha = item.getString("sha256").toLowerCase();
                editor.putString(key(uuid), sha);
            }
            editor.apply();
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Invalid preseeded watchface manifest", error);
        }
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] value = digest.digest();
            StringBuilder result = new StringBuilder(value.length * 2);
            for (byte item : value) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MANIFEST_BYTES) {
                    throw new IOException("Preseeded manifest is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String key(UUID uuid) {
        return PREFIX + uuid.toString();
    }
}
