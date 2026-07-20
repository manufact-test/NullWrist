package com.manufacttest.pebblereardisplay.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;
import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Loads bundled QEMU screenshots and stores real frames captured for imported PBWs. */
public final class WatchfaceThumbnailRepository {
    public static final String ACTION_THUMBNAIL_UPDATED =
            "com.manufacttest.pebblereardisplay.action.THUMBNAIL_UPDATED";
    public static final String EXTRA_STORAGE_ID = "storage_id";

    private static final String ASSET_DIRECTORY = "watchface-thumbnails";
    private static final String FILE_DIRECTORY = "watchface-thumbnails";
    private static final String CAPTURE_PREFERENCES = "watchface_thumbnail_captures";
    private static final int CAPTURE_SCHEMA_VERSION = 3;

    private final Context context;
    private final File directory;

    public WatchfaceThumbnailRepository(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), FILE_DIRECTORY);
    }

    public boolean hasThumbnail(WatchfaceMetadata metadata) {
        String fileName = fileName(metadata);
        if (new File(directory, fileName).isFile()) {
            return true;
        }
        try (InputStream ignored = context.getAssets().open(ASSET_DIRECTORY + "/" + fileName)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public boolean hasCurrentThumbnail(WatchfaceMetadata metadata) {
        if (metadata.isBundled()) {
            return hasThumbnail(metadata);
        }
        File captured = new File(directory, fileName(metadata));
        int version = context.getSharedPreferences(
                CAPTURE_PREFERENCES,
                Context.MODE_PRIVATE
        ).getInt(captureKey(metadata), 0);
        return captured.isFile() && version >= CAPTURE_SCHEMA_VERSION;
    }

    public Bitmap load(WatchfaceMetadata metadata) {
        String fileName = fileName(metadata);
        File captured = new File(directory, fileName);
        if (captured.isFile()) {
            Bitmap bitmap = decodeFile(captured);
            if (bitmap != null) {
                return bitmap;
            }
        }

        try (InputStream input = new BufferedInputStream(
                context.getAssets().open(ASSET_DIRECTORY + "/" + fileName)
        )) {
            return decode(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    public boolean capture(PebbleQemuProcess runtime, WatchfaceMetadata metadata) {
        int[] pixels = new int[PebbleQemuProcess.WIDTH * PebbleQemuProcess.HEIGHT];
        if (!runtime.readFrame(pixels)) {
            return false;
        }

        if (!directory.isDirectory() && !directory.mkdirs()) {
            return false;
        }
        File destination = new File(directory, fileName(metadata));
        File temporary = new File(directory, destination.getName() + ".tmp");
        Bitmap bitmap = Bitmap.createBitmap(
                pixels,
                PebbleQemuProcess.WIDTH,
                PebbleQemuProcess.HEIGHT,
                Bitmap.Config.ARGB_8888
        );
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
        } catch (IOException error) {
            temporary.delete();
            return false;
        } finally {
            bitmap.recycle();
        }

        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            return false;
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            return false;
        }
        context.getSharedPreferences(CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(captureKey(metadata), CAPTURE_SCHEMA_VERSION)
                .apply();
        return true;
    }

    public void delete(WatchfaceMetadata metadata) {
        File captured = new File(directory, fileName(metadata));
        if (captured.isFile()) {
            captured.delete();
        }
        context.getSharedPreferences(CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(captureKey(metadata))
                .apply();
    }

    private Bitmap decodeFile(File file) {
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            return decode(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Bitmap decode(InputStream input) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeStream(input, null, options);
    }

    private static String captureKey(WatchfaceMetadata metadata) {
        return "capture:" + fileName(metadata);
    }

    private static String fileName(WatchfaceMetadata metadata) {
        if (!metadata.isBundled()) {
            return "imported-"
                    + Integer.toUnsignedString(metadata.getStorageId().hashCode(), 16)
                    + ".png";
        }
        String uuid = metadata.getUuid() == null ? "" : metadata.getUuid()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "");
        if (uuid.isEmpty() || uuid.contains("unknown")) {
            uuid = "storage-" + Integer.toUnsignedString(metadata.getStorageId().hashCode(), 16);
        }
        return uuid + ".png";
    }
}
