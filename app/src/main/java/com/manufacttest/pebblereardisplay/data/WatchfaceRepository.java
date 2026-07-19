package com.manufacttest.pebblereardisplay.data;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;

import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class WatchfaceRepository {
    private static final long MAX_PBW_BYTES = 20L * 1024L * 1024L;
    private static final String ASSET_DIRECTORY = "watchfaces";
    private static final String LIBRARY_PREFERENCES = "watchface_library";
    private static final String KEY_HIDDEN_BUNDLED = "hidden_bundled";
    private static final String[] RETIRED_BUNDLED_FILES = {
            "yweather-3.7.pbw"
    };

    private final Context context;
    private final File watchfaceDirectory;

    public WatchfaceRepository(Context context) {
        this.context = context.getApplicationContext();
        watchfaceDirectory = new File(this.context.getFilesDir(), "watchfaces");
    }

    public List<WatchfaceMetadata> loadAll() throws IOException {
        ensureDirectory();
        removeRetiredBundledFiles();
        List<WatchfaceMetadata> result = new ArrayList<>();
        Set<String> seenStorageIds = new HashSet<>();

        copyAndReadBundled(result, seenStorageIds);
        readStoredFiles(result, seenStorageIds);

        result.sort(Comparator.comparing(WatchfaceMetadata::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public WatchfaceMetadata importFromUri(Uri uri) throws IOException {
        ensureDirectory();
        ContentResolver resolver = context.getContentResolver();
        File temporary = new File(watchfaceDirectory, ".import-" + UUID.randomUUID() + ".tmp");

        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) {
                throw new IOException("Cannot open selected file");
            }
            copyLimited(input, output, MAX_PBW_BYTES);
        } catch (IOException exception) {
            temporary.delete();
            throw exception;
        }

        WatchfaceMetadata parsed;
        try (FileInputStream input = new FileInputStream(temporary)) {
            parsed = PbwParser.parse(input, temporary.getName(), false);
        } catch (IOException exception) {
            temporary.delete();
            throw exception;
        }

        String safeName = sanitize(parsed.getName());
        String uuidPart = parsed.getUuid().replaceAll("[^A-Za-z0-9-]", "");
        if (uuidPart.isEmpty()) {
            uuidPart = UUID.randomUUID().toString();
        }
        File destination = new File(watchfaceDirectory, safeName + "-" + uuidPart + ".pbw");
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IOException("Cannot replace existing watchface");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Cannot store imported watchface");
        }

        try (FileInputStream input = new FileInputStream(destination)) {
            return PbwParser.parse(input, destination.getName(), false);
        }
    }

    public WatchfaceMetadata findByStorageId(String storageId) throws IOException {
        if (storageId == null) {
            return null;
        }
        for (WatchfaceMetadata metadata : loadAll()) {
            if (storageId.equals(metadata.getStorageId())) {
                return metadata;
            }
        }
        return null;
    }

    public File fileFor(WatchfaceMetadata metadata) {
        return new File(watchfaceDirectory, metadata.getStorageId());
    }

    public void delete(WatchfaceMetadata metadata) throws IOException {
        if (metadata == null) {
            throw new IOException("No watchface was selected for deletion");
        }
        ensureDirectory();
        File stored = fileFor(metadata);
        if (stored.isFile() && !stored.delete()) {
            throw new IOException("Cannot delete " + metadata.getName());
        }
        if (metadata.isBundled()) {
            Set<String> hidden = hiddenBundledStorageIds();
            hidden.add(metadata.getStorageId());
            context.getSharedPreferences(LIBRARY_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEY_HIDDEN_BUNDLED, hidden)
                    .apply();
        }
    }

    private void copyAndReadBundled(
            List<WatchfaceMetadata> output,
            Set<String> seenStorageIds
    ) throws IOException {
        AssetManager assets = context.getAssets();
        String[] names = assets.list(ASSET_DIRECTORY);
        if (names == null) {
            return;
        }
        Set<String> hiddenBundled = hiddenBundledStorageIds();

        for (String name : names) {
            if (!name.toLowerCase(Locale.ROOT).endsWith(".pbw")
                    || hiddenBundled.contains(name)) {
                continue;
            }
            File destination = new File(watchfaceDirectory, name);
            if (!destination.exists()) {
                try (InputStream input = assets.open(ASSET_DIRECTORY + "/" + name);
                     FileOutputStream fileOutput = new FileOutputStream(destination)) {
                    copyLimited(input, fileOutput, MAX_PBW_BYTES);
                }
            }
            try (FileInputStream input = new FileInputStream(destination)) {
                output.add(PbwParser.parse(input, name, true));
                seenStorageIds.add(name);
            }
        }
    }

    private void readStoredFiles(
            List<WatchfaceMetadata> output,
            Set<String> seenStorageIds
    ) {
        File[] files = watchfaceDirectory.listFiles();
        if (files == null) {
            return;
        }
        Set<String> hiddenBundled = hiddenBundledStorageIds();
        for (File file : files) {
            if (!file.isFile()
                    || !file.getName().toLowerCase(Locale.ROOT).endsWith(".pbw")
                    || seenStorageIds.contains(file.getName())
                    || hiddenBundled.contains(file.getName())) {
                continue;
            }
            try (FileInputStream input = new FileInputStream(file)) {
                output.add(PbwParser.parse(input, file.getName(), false));
            } catch (IOException ignored) {
                // Invalid files stay out of the catalog; an import error is shown at import time.
            }
        }
    }

    private Set<String> hiddenBundledStorageIds() {
        SharedPreferences preferences = context.getSharedPreferences(
                LIBRARY_PREFERENCES,
                Context.MODE_PRIVATE
        );
        Set<String> stored = preferences.getStringSet(KEY_HIDDEN_BUNDLED, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private void removeRetiredBundledFiles() {
        for (String name : RETIRED_BUNDLED_FILES) {
            File retired = new File(watchfaceDirectory, name);
            if (retired.isFile()) {
                retired.delete();
            }
        }
    }

    private void ensureDirectory() throws IOException {
        if (!watchfaceDirectory.exists() && !watchfaceDirectory.mkdirs()) {
            throw new IOException("Cannot create watchface directory");
        }
    }

    private static void copyLimited(InputStream input, FileOutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("PBW is larger than 20 MB");
            }
            output.write(buffer, 0, read);
        }
    }

    private static String sanitize(String value) {
        String safe = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "watchface" : safe;
    }
}
