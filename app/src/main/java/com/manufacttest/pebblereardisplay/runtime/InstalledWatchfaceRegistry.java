package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Mirrors the apps persisted in the emulator SPI flash using PBW SHA-256 fingerprints. */
public final class InstalledWatchfaceRegistry {
    private static final String PREFS = "installed_watchfaces_v1";
    private static final String PREFIX = "watchface_";

    private final SharedPreferences preferences;

    public InstalledWatchfaceRegistry(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    private static String key(UUID uuid) {
        return PREFIX + uuid.toString();
    }
}
