#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one exact match, found {count}")
    write(path, text.replace(old, new, 1))


def replace_regex(path: str, pattern: str, replacement: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{path}: regex did not match exactly once")
    write(path, updated)


APP_PREFERENCES = r'''package com.manufacttest.pebblereardisplay.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public final class AppPreferences {
    private static final String FILE_NAME = "pebble_rear_display";
    private static final String KEY_SELECTED_WATCHFACE = "selected_watchface";
    private static final String KEY_SLEEP_SCHEDULE_ENABLED = "sleep_schedule_enabled";
    private static final String KEY_SLEEP_START_MINUTES = "sleep_start_minutes";
    private static final String KEY_SLEEP_END_MINUTES = "sleep_end_minutes";

    private static final int DEFAULT_SLEEP_START_MINUTES = 0;
    private static final int DEFAULT_SLEEP_END_MINUTES = 7 * 60;

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedWatchfaceId() {
        return preferences.getString(KEY_SELECTED_WATCHFACE, null);
    }

    public void setSelectedWatchfaceId(String storageId) {
        preferences.edit().putString(KEY_SELECTED_WATCHFACE, storageId).apply();
    }

    public void clearSelectedWatchfaceId() {
        preferences.edit().remove(KEY_SELECTED_WATCHFACE).apply();
    }

    /** Night Mode is intentionally always active; the chosen interval is the only setting. */
    public boolean isSleepScheduleEnabled() {
        return true;
    }

    public int getSleepStartMinutes() {
        return clampMinutes(preferences.getInt(
                KEY_SLEEP_START_MINUTES,
                DEFAULT_SLEEP_START_MINUTES
        ));
    }

    public int getSleepEndMinutes() {
        return clampMinutes(preferences.getInt(
                KEY_SLEEP_END_MINUTES,
                DEFAULT_SLEEP_END_MINUTES
        ));
    }

    public void setSleepSchedule(int startMinutes, int endMinutes) {
        preferences.edit()
                .putBoolean(KEY_SLEEP_SCHEDULE_ENABLED, true)
                .putInt(KEY_SLEEP_START_MINUTES, clampMinutes(startMinutes))
                .putInt(KEY_SLEEP_END_MINUTES, clampMinutes(endMinutes))
                .apply();
    }

    /** Compatibility overload for 0.8.4 callers and stored preferences. */
    public void setSleepSchedule(boolean ignored, int startMinutes, int endMinutes) {
        setSleepSchedule(startMinutes, endMinutes);
    }

    public static String formatMinutes(int minutes) {
        int value = clampMinutes(minutes);
        return String.format(Locale.US, "%02d:%02d", value / 60, value % 60);
    }

    private static int clampMinutes(int minutes) {
        return Math.max(0, Math.min(24 * 60 - 1, minutes));
    }
}
'''

PEBBLE_APP_INSTALLER = r'''package com.manufacttest.pebblereardisplay.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Installs and launches modern Pebble apps using BlobDB, AppFetch and PutBytes. */
public final class PebbleAppInstaller {
    private static final int ENDPOINT_APP_RUN_STATE = 0x0034;
    private static final int ENDPOINT_APP_FETCH = 0x1771;
    private static final int ENDPOINT_BLOB_DB = 0xB1DB;
    private static final int ENDPOINT_PUT_BYTES = 0xBEEF;

    private static final int APP_RUN_STATE_RUNNING = 0x01;
    private static final int APP_RUN_STATE_RUN_COMMAND = 0x01;
    private static final int APP_RUN_STATE_STATUS_COMMAND = 0x03;
    private static final int BLOB_DATABASE_APP = 2;
    private static final int BLOB_STATUS_SUCCESS = 1;
    private static final int BLOB_STATUS_TRY_LATER = 0x0B;
    private static final int PUT_BYTES_ACK = 1;

    private static final int PART_RESOURCES = 4;
    private static final int PART_BINARY = 5;
    private static final int PART_WORKER = 7;
    private static final int APP_INSTALL_FLAG = 0x80;
    private static final int TRANSFER_CHUNK_BYTES = 1000;
    private static final long TRANSFER_PACING_MILLIS = 4L;
    private static final long PUT_BYTES_TIMEOUT_MILLIS = 30_000L;
    private static final long RUN_CONFIRM_TIMEOUT_MILLIS = 12_000L;

    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(0x4100);

    private final PebbleProtocolLink link;
    private final ProgressListener progressListener;
    private int totalSent;
    private int totalSize;
    private int lastPublishedPercent = -1;
    private long lastPublishedNanos;

    public PebbleAppInstaller(PebbleProtocolLink link, ProgressListener progressListener) {
        this.link = link;
        this.progressListener = progressListener;
    }

    public void install(PebblePbwBundle bundle) throws IOException, InterruptedException {
        PebblePbwBundle.AppHeader header = bundle.getHeader();
        totalSent = 0;
        totalSize = bundle.getTotalTransferBytes();
        publish("Preparing " + header.getAppName());

        insertAppMetadata(header);
        int installId = requestAppStart(header.getUuid());
        sendPart(PART_BINARY, bundle.getApplication(), installId);
        if (bundle.getResources() != null) {
            sendPart(PART_RESOURCES, bundle.getResources(), installId);
        }
        if (bundle.getWorker() != null) {
            sendPart(PART_WORKER, bundle.getWorker(), installId);
        }
        awaitRunning(header.getUuid(), header.getAppName(), RUN_CONFIRM_TIMEOUT_MILLIS);
        publish("Launching " + header.getAppName());
    }

    /** Launches an app already present in Pebble AppDB/SPI flash and waits for its UUID. */
    public void launch(UUID uuid, String appName) throws IOException, InterruptedException {
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);
        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) APP_RUN_STATE_RUN_COMMAND);
        start.put(uuidBytes(uuid));
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());
        awaitRunning(uuid, appName, RUN_CONFIRM_TIMEOUT_MILLIS);
    }

    private void insertAppMetadata(PebblePbwBundle.AppHeader header)
            throws IOException, InterruptedException {
        byte[] uuid = uuidBytes(header.getUuid());
        byte[] metadata = appMetadata(header, uuid);

        for (int attempt = 0; attempt < 8; attempt++) {
            int token = nextToken();
            ByteBuffer payload = ByteBuffer.allocate(1 + 2 + 1 + 1 + 16 + 2 + metadata.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            payload.put((byte) 0x01);
            payload.putShort((short) token);
            payload.put((byte) BLOB_DATABASE_APP);
            payload.put((byte) uuid.length);
            payload.put(uuid);
            payload.putShort((short) metadata.length);
            payload.put(metadata);
            link.clearEndpoint(ENDPOINT_BLOB_DB);
            link.sendPebblePacket(ENDPOINT_BLOB_DB, payload.array());

            byte[] response = link.awaitEndpoint(
                    ENDPOINT_BLOB_DB,
                    value -> value.length >= 3 && unsignedShortLe(value, 0) == token,
                    8_000
            );
            int status = response[2] & 0xff;
            if (status == BLOB_STATUS_SUCCESS) {
                return;
            }
            if (status == BLOB_STATUS_TRY_LATER) {
                Thread.sleep(250L * (attempt + 1));
                continue;
            }
            throw new IOException("Pebble AppDB rejected metadata with status " + status);
        }
        throw new IOException("Pebble AppDB remained busy");
    }

    private int requestAppStart(UUID uuid) throws IOException, InterruptedException {
        byte[] rawUuid = uuidBytes(uuid);
        link.clearEndpoint(ENDPOINT_APP_FETCH);
        link.clearEndpoint(ENDPOINT_APP_RUN_STATE);

        ByteBuffer start = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        start.put((byte) APP_RUN_STATE_RUN_COMMAND);
        start.put(rawUuid);
        link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, start.array());

        byte[] fetch = link.awaitEndpoint(
                ENDPOINT_APP_FETCH,
                value -> value.length >= 21
                        && (value[0] & 0xff) == 0x01
                        && Arrays.equals(Arrays.copyOfRange(value, 1, 17), rawUuid),
                15_000
        );
        return ByteBuffer.wrap(fetch, 17, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void sendPart(int partType, byte[] object, int installId)
            throws IOException, InterruptedException {
        ByteBuffer init = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        init.put((byte) 0x01);
        init.putInt(object.length);
        init.put((byte) (partType | APP_INSTALL_FLAG));
        init.putInt(installId);
        int cookie = sendPutBytes(init.array(), null).cookie;

        for (int offset = 0; offset < object.length; offset += TRANSFER_CHUNK_BYTES) {
            int length = Math.min(TRANSFER_CHUNK_BYTES, object.length - offset);
            ByteBuffer put = ByteBuffer.allocate(9 + length).order(ByteOrder.BIG_ENDIAN);
            put.put((byte) 0x02);
            put.putInt(cookie);
            put.putInt(length);
            put.put(object, offset, length);
            sendPutBytes(put.array(), cookie);
            totalSent += length;
            publishProgress();
            Thread.sleep(TRANSFER_PACING_MILLIS);
        }

        ByteBuffer commit = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
        commit.put((byte) 0x03);
        commit.putInt(cookie);
        commit.putInt(stm32Crc32(object));
        sendPutBytes(commit.array(), cookie);

        ByteBuffer install = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        install.put((byte) 0x05);
        install.putInt(cookie);
        sendPutBytes(install.array(), cookie);
    }

    private PutBytesResponse sendPutBytes(byte[] payload, Integer expectedCookie)
            throws IOException, InterruptedException {
        link.clearEndpoint(ENDPOINT_PUT_BYTES);
        link.sendPebblePacket(ENDPOINT_PUT_BYTES, payload);
        byte[] response = link.awaitEndpoint(
                ENDPOINT_PUT_BYTES,
                value -> isPutBytesResponseFor(value, expectedCookie),
                PUT_BYTES_TIMEOUT_MILLIS
        );
        int result = response[0] & 0xff;
        int cookie = putBytesCookie(response);
        if (result != PUT_BYTES_ACK) {
            throw new IOException(
                    "Pebble NACKed PutBytes for token " + Integer.toUnsignedString(cookie)
            );
        }
        return new PutBytesResponse(cookie);
    }

    private void awaitRunning(UUID uuid, String appName, long timeoutMillis)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(
                    1L,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            );
            try {
                link.awaitEndpoint(
                        ENDPOINT_APP_RUN_STATE,
                        value -> isRunningStateFor(value, uuid),
                        Math.min(650L, remaining)
                );
                return;
            } catch (IOException error) {
                if (!isEndpointTimeout(error)) {
                    throw error;
                }
            }

            link.clearEndpoint(ENDPOINT_APP_RUN_STATE);
            ByteBuffer status = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
            status.put((byte) APP_RUN_STATE_STATUS_COMMAND);
            status.put(new byte[16]);
            link.sendPebblePacket(ENDPOINT_APP_RUN_STATE, status.array());
        }
        throw new IOException("PebbleOS did not confirm " + appName + " as the running app");
    }

    static boolean isRunningStateFor(byte[] value, UUID expectedUuid) {
        if (value == null || value.length < 17 || (value[0] & 0xff) != APP_RUN_STATE_RUNNING) {
            return false;
        }
        return Arrays.equals(
                Arrays.copyOfRange(value, 1, 17),
                uuidBytes(expectedUuid)
        );
    }

    static boolean isPutBytesResponseFor(byte[] value, Integer expectedCookie) {
        return value != null
                && value.length >= 5
                && (expectedCookie == null || putBytesCookie(value) == expectedCookie);
    }

    static int putBytesCookie(byte[] response) {
        return ByteBuffer.wrap(response, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static boolean isEndpointTimeout(IOException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("Timed out waiting for Pebble endpoint");
    }

    private void publishProgress() {
        if (progressListener == null) {
            return;
        }
        int percent = totalSize <= 0 ? 0 : Math.min(100, Math.round(totalSent * 100f / totalSize));
        long now = System.nanoTime();
        if (percent < 100
                && percent - lastPublishedPercent < 2
                && now - lastPublishedNanos < TimeUnit.MILLISECONDS.toNanos(200)) {
            return;
        }
        lastPublishedPercent = percent;
        lastPublishedNanos = now;
        progressListener.onProgress("Installing watchface… " + percent + "%", totalSent, totalSize);
    }

    private void publish(String message) {
        if (progressListener != null) {
            progressListener.onProgress(message, totalSent, totalSize);
        }
    }

    private static byte[] appMetadata(PebblePbwBundle.AppHeader header, byte[] uuid) {
        ByteBuffer metadata = ByteBuffer.allocate(126).order(ByteOrder.LITTLE_ENDIAN);
        metadata.put(uuid);
        metadata.putInt(header.getFlags());
        metadata.putInt(header.getIconResourceId());
        metadata.put((byte) header.getAppVersionMajor());
        metadata.put((byte) header.getAppVersionMinor());
        metadata.put((byte) header.getSdkVersionMajor());
        metadata.put((byte) header.getSdkVersionMinor());
        metadata.put((byte) 0);
        metadata.put((byte) 0);

        byte[] name = header.getAppName().getBytes(StandardCharsets.UTF_8);
        metadata.put(name, 0, Math.min(name.length, 96));
        while (metadata.position() < metadata.capacity()) {
            metadata.put((byte) 0);
        }
        return metadata.array();
    }

    static int stm32Crc32(byte[] data) {
        int crc = 0xffffffff;
        for (int offset = 0; offset < data.length; offset += 4) {
            int remaining = Math.min(4, data.length - offset);
            int word;
            if (remaining == 4) {
                word = (data[offset] & 0xff)
                        | ((data[offset + 1] & 0xff) << 8)
                        | ((data[offset + 2] & 0xff) << 16)
                        | ((data[offset + 3] & 0xff) << 24);
            } else {
                word = 0;
                for (int index = 0; index < remaining; index++) {
                    word = (word << 8) | (data[offset + index] & 0xff);
                }
            }
            crc ^= word;
            for (int bit = 0; bit < 32; bit++) {
                crc = (crc & 0x80000000) != 0
                        ? (crc << 1) ^ 0x04C11DB7
                        : crc << 1;
            }
        }
        return crc;
    }

    private static byte[] uuidBytes(UUID uuid) {
        ByteBuffer value = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        value.putLong(uuid.getMostSignificantBits());
        value.putLong(uuid.getLeastSignificantBits());
        return value.array();
    }

    private static int nextToken() {
        int token = NEXT_TOKEN.updateAndGet(value -> value >= 0xfffe ? 1 : value + 1);
        return token & 0xffff;
    }

    private static int unsignedShortLe(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    public interface ProgressListener {
        void onProgress(String message, int sentBytes, int totalBytes);
    }

    private static final class PutBytesResponse {
        final int cookie;

        PutBytesResponse(int cookie) {
            this.cookie = cookie;
        }
    }
}
'''

REAR_DISPLAY_ACTIVITY = r'''package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public final class RearDisplayActivity extends Activity {
    private boolean previewMode;
    private View rearSurface;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        previewMode = getIntent().getBooleanExtra(DisplayUtils.EXTRA_PREVIEW_MODE, false);

        try {
            rearSurface = new PebbleOsSurfaceView(this);
        } catch (RuntimeException exception) {
            TextView fallback = new TextView(this);
            fallback.setBackgroundColor(Color.BLACK);
            fallback.setTextColor(Color.WHITE);
            fallback.setGravity(Gravity.CENTER);
            fallback.setText("Rear display could not be initialized\n"
                    + exception.getClass().getSimpleName());
            rearSurface = fallback;
        }

        View content = rearSurface;
        if (previewMode) {
            FrameLayout preview = new FrameLayout(this);
            preview.setBackgroundColor(Color.BLACK);
            preview.addView(rearSurface, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            TextView hint = new TextView(this);
            hint.setText("PRESS THE PHONE'S BACK KEY TO EXIT PREVIEW");
            hint.setTextColor(0xffffd84d);
            hint.setTextSize(11);
            hint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(12), dp(9), dp(12), dp(9));
            GradientDrawable hintBackground = new GradientDrawable();
            hintBackground.setColor(0xe61b1b1b);
            hintBackground.setStroke(dp(1), 0xffffd84d);
            hintBackground.setCornerRadius(dp(2));
            hint.setBackground(hintBackground);

            FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            hintParams.setMargins(dp(12), dp(12), dp(12), dp(12));
            preview.addView(hint, hintParams);
            content = preview;
        }

        setContentView(content);
        rearSurface.setClickable(true);
        rearSurface.setFocusable(true);
        rearSurface.setFocusableInTouchMode(true);
        rearSurface.requestFocus();
        registerBackGuard();
        rearSurface.post(() -> RearUi.lockRearSurface(this, rearSurface));
    }

    private void registerBackGuard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        backCallback = () -> {
            if (previewMode) {
                finish();
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                backCallback
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rearSurface != null) {
            RearUi.lockRearSurface(this, rearSurface);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (previewMode && keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (previewMode) {
            finish();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        RearUi.enterImmersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && rearSurface != null) {
            RearUi.lockRearSurface(this, rearSurface);
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        if (previewMode) {
            RearUi.leaveImmersive(this);
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
'''

INSTALLER_TEST = r'''package com.manufacttest.pebblereardisplay.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PebbleAppInstallerTest {
    @Test
    public void crcMatchesPebblePutBytesVectors() {
        assertCrc(0xffffffffL, new byte[0]);
        assertCrc(0x6f60065bL, "a".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xd1dfbb34L, "ab".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xeb13e4f7L, "abc".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xa62f1c36L, "abcd".getBytes(StandardCharsets.UTF_8));
        assertCrc(0x169f68cbL, "hello world".getBytes(StandardCharsets.UTF_8));
        assertCrc(0xa8e4ad71L, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    }

    @Test
    public void appRunStateRequiresRunningAndExactUuid() {
        UUID expected = UUID.fromString("13371337-0ebc-456b-a94f-6d07f39de93d");
        UUID other = UUID.fromString("13371337-6896-46c3-ab9c-0f4d5a2c421d");

        assertTrue(PebbleAppInstaller.isRunningStateFor(runState(1, expected), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(runState(2, expected), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(runState(1, other), expected));
        assertFalse(PebbleAppInstaller.isRunningStateFor(new byte[]{1}, expected));
    }

    @Test
    public void putBytesResponseRejectsStaleCookie() {
        byte[] response = ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) 1)
                .putInt(0x11223344)
                .array();

        assertTrue(PebbleAppInstaller.isPutBytesResponseFor(response, null));
        assertTrue(PebbleAppInstaller.isPutBytesResponseFor(response, 0x11223344));
        assertFalse(PebbleAppInstaller.isPutBytesResponseFor(response, 0x55667788));
        assertEquals(0x11223344, PebbleAppInstaller.putBytesCookie(response));
    }

    private static byte[] runState(int state, UUID uuid) {
        ByteBuffer payload = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN);
        payload.put((byte) state);
        payload.putLong(uuid.getMostSignificantBits());
        payload.putLong(uuid.getLeastSignificantBits());
        return payload.array();
    }

    private static void assertCrc(long expectedUnsigned, byte[] value) {
        assertEquals(expectedUnsigned, Integer.toUnsignedLong(PebbleAppInstaller.stm32Crc32(value)));
    }
}
'''


def main() -> None:
    write("app/src/main/java/com/manufacttest/pebblereardisplay/data/AppPreferences.java", APP_PREFERENCES)
    write("app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleAppInstaller.java", PEBBLE_APP_INSTALLER)
    write("app/src/main/java/com/manufacttest/pebblereardisplay/ui/RearDisplayActivity.java", REAR_DISPLAY_ACTIVITY)
    write("app/src/test/java/com/manufacttest/pebblereardisplay/runtime/PebbleAppInstallerTest.java", INSTALLER_TEST)

    protocol = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleProtocolLink.java"
    replace_once(
        protocol,
        '''    public String diagnostics() {\n        String endpoint = lastEndpoint < 0\n                ? "none"\n                : String.format("0x%04X", lastEndpoint);\n        return "qemuFrames=" + qemuFramesReceived\n                + ", sppFrames=" + sppFramesReceived\n                + ", pebblePackets=" + pebblePacketsReceived\n                + ", lastEndpoint=" + endpoint;\n    }\n''',
        '''    public String diagnostics() {\n        String endpoint = lastEndpoint < 0\n                ? "none"\n                : String.format("0x%04X", lastEndpoint);\n        return "qemuFrames=" + qemuFramesReceived\n                + ", sppFrames=" + sppFramesReceived\n                + ", pebblePackets=" + pebblePacketsReceived\n                + ", lastEndpoint=" + endpoint;\n    }\n\n    /** Drops stale asynchronous responses before beginning a new protocol transaction. */\n    public void clearEndpoint(int endpoint) {\n        endpointQueues.computeIfAbsent(\n                endpoint,\n                ignored -> new LinkedBlockingQueue<>()\n        ).clear();\n    }\n\n    public boolean isHealthy() {\n        return running && readerFailure == null && !socket.isClosed();\n    }\n'''
    )

    qemu = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleQemuProcess.java"
    replace_regex(
        qemu,
        r'''    public boolean activateWatchface\(\n            File pbwFile,\n            InstalledWatchfaceRegistry registry,\n            PebbleAppInstaller\.ProgressListener progressListener\n    \) throws IOException, InterruptedException \{.*?\n    \}\n\n    public void installWatchface''',
        '''    public boolean activateWatchface(\n            File pbwFile,\n            InstalledWatchfaceRegistry registry,\n            PebbleAppInstaller.ProgressListener progressListener\n    ) throws IOException, InterruptedException {\n        if (pbwFile == null || !pbwFile.isFile()) {\n            throw new IOException("Selected PBW file is missing");\n        }\n        if (!isRunning()) {\n            throw new IOException("PebbleOS is not running");\n        }\n        if (!firmwareReady) {\n            waitForFirmwareReady(30_000);\n        }\n\n        String fingerprint = InstalledWatchfaceRegistry.sha256(pbwFile);\n        PebbleProtocolLink link = protocolLink(20_000);\n        try (PebblePbwBundle bundle = new PebblePbwBundle(pbwFile)) {\n            PebblePbwBundle.AppHeader header = bundle.getHeader();\n            if (registry.isInstalled(header.getUuid(), fingerprint)) {\n                try {\n                    new PebbleAppInstaller(link, progressListener).launch(\n                            header.getUuid(),\n                            header.getAppName()\n                    );\n                } catch (IOException firstFailure) {\n                    invalidateProtocolLink(link);\n                    link = protocolLink(8_000);\n                    new PebbleAppInstaller(link, progressListener).launch(\n                            header.getUuid(),\n                            header.getAppName()\n                    );\n                }\n                return false;\n            }\n            new PebbleAppInstaller(link, progressListener).install(bundle);\n            registry.markInstalled(header.getUuid(), fingerprint);\n            return true;\n        } catch (IOException error) {\n            invalidateProtocolLink(link);\n            throw error;\n        }\n    }\n\n    public void installWatchface'''
    )
    replace_regex(
        qemu,
        r'''    private PebbleProtocolLink protocolLink\(long timeoutMillis\)\n            throws IOException, InterruptedException \{.*?\n    \}\n\n    private void prepareFiles''',
        '''    private PebbleProtocolLink protocolLink(long timeoutMillis)\n            throws IOException, InterruptedException {\n        PebbleProtocolLink stale = null;\n        synchronized (this) {\n            if (protocolLink != null && protocolLink.isHealthy()) {\n                return protocolLink;\n            }\n            stale = protocolLink;\n            protocolLink = null;\n        }\n        if (stale != null) {\n            stale.close();\n        }\n\n        PebbleProtocolLink candidate = PebbleProtocolLink.connect(\n                "127.0.0.1",\n                protocolPort,\n                timeoutMillis\n        );\n        synchronized (this) {\n            if (!isRunning()) {\n                candidate.close();\n                throw new IOException("QEMU stopped before the protocol connection completed");\n            }\n            if (protocolLink == null) {\n                protocolLink = candidate;\n            } else {\n                candidate.close();\n            }\n            return protocolLink;\n        }\n    }\n\n    private void invalidateProtocolLink(PebbleProtocolLink expected) {\n        synchronized (this) {\n            if (protocolLink == expected) {\n                protocolLink = null;\n            }\n        }\n        if (expected != null) {\n            expected.close();\n        }\n    }\n\n    private void prepareFiles'''
    )

    service = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
    replace_once(
        service,
        '''            if (selected.metadata.getStorageId().equals(activeStorageId)) {\n                return;\n            }\n            current.resume();\n            activateSelected(current, selected);\n''',
        '''            if (selected.metadata.getStorageId().equals(activeStorageId)) {\n                return;\n            }\n            // Cancel a pending capture before the framebuffer starts showing install/launch frames.\n            thumbnailGeneration.incrementAndGet();\n            current.resume();\n            activateSelected(current, selected);\n'''
    )
    replace_regex(
        service,
        r'''    private void activateSelected\(PebbleQemuProcess current, SelectedWatchface selected\)\n            throws IOException, InterruptedException \{.*?\n    \}\n\n    private boolean restorePrevious''',
        '''    private void activateSelected(PebbleQemuProcess current, SelectedWatchface selected)\n            throws IOException, InterruptedException {\n        status = null;\n        failure = null;\n        notifyListeners();\n\n        int frameBeforeLaunch = current.readFrameSequence();\n        boolean installed = current.activateWatchface(\n                selected.file,\n                new InstalledWatchfaceRegistry(this),\n                null\n        );\n\n        // AppRunState has already confirmed the exact UUID. The framebuffer is now only a\n        // rendering check, never the command acknowledgement.\n        int frameAfterConfirmation = current.readFrameSequence();\n        boolean rendered = frameAfterConfirmation > 0\n                && frameAfterConfirmation != frameBeforeLaunch;\n        if (!rendered && !waitForFrameAdvance(\n                current,\n                frameAfterConfirmation,\n                installed ? 8_000 : 5_000\n        )) {\n            throw new IOException("Confirmed watchface did not render a framebuffer");\n        }\n\n        activeStorageId = selected.metadata.getStorageId();\n        starting = false;\n        status = null;\n        failure = null;\n        updateNotification("Running " + selected.metadata.getName());\n        notifyListeners();\n        scheduleThumbnailCapture(current, selected.metadata);\n    }\n\n    private boolean restorePrevious'''
    )
    replace_once(
        service,
        '''        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);\n        if (thumbnails.hasCurrentThumbnail(metadata)) {\n            return;\n        }\n        int token = thumbnailGeneration.incrementAndGet();\n''',
        '''        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);\n        int token = thumbnailGeneration.incrementAndGet();\n        if (thumbnails.hasCurrentThumbnail(metadata)) {\n            return;\n        }\n'''
    )

    policy = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/RuntimePowerPolicy.java"
    replace_once(
        policy,
        '''        if (preferences.isSleepScheduleEnabled()\n                && isInsideSchedule(\n                minuteOfDay,\n                preferences.getSleepStartMinutes(),\n                preferences.getSleepEndMinutes()\n        )) {\n''',
        '''        if (isInsideSchedule(\n                minuteOfDay,\n                preferences.getSleepStartMinutes(),\n                preferences.getSleepEndMinutes()\n        )) {\n'''
    )

    thumbnails = "app/src/main/java/com/manufacttest/pebblereardisplay/data/WatchfaceThumbnailRepository.java"
    replace_once(thumbnails, "private static final int CAPTURE_SCHEMA_VERSION = 2;", "private static final int CAPTURE_SCHEMA_VERSION = 3;")
    replace_regex(
        thumbnails,
        r'''    private static String fileName\(WatchfaceMetadata metadata\) \{.*?\n    \}\n''',
        '''    private static String fileName(WatchfaceMetadata metadata) {\n        if (!metadata.isBundled()) {\n            return "imported-"\n                    + Integer.toUnsignedString(metadata.getStorageId().hashCode(), 16)\n                    + ".png";\n        }\n        String uuid = metadata.getUuid() == null ? "" : metadata.getUuid()\n                .toLowerCase(Locale.ROOT)\n                .replaceAll("[^a-z0-9-]", "");\n        if (uuid.isEmpty() || uuid.contains("unknown")) {\n            uuid = "storage-" + Integer.toUnsignedString(metadata.getStorageId().hashCode(), 16);\n        }\n        return uuid + ".png";\n    }\n'''
    )

    repository = "app/src/main/java/com/manufacttest/pebblereardisplay/data/WatchfaceRepository.java"
    replace_once(
        repository,
        '''        String safeName = sanitize(parsed.getName());\n        String uuidPart = parsed.getUuid().replaceAll("[^A-Za-z0-9-]", "");\n        if (uuidPart.isEmpty()) {\n            uuidPart = UUID.randomUUID().toString();\n        }\n        File destination = new File(watchfaceDirectory, safeName + "-" + uuidPart + ".pbw");\n''',
        '''        String uuidPart = parsed.getUuid().replaceAll("[^A-Za-z0-9-]", "");\n        if (uuidPart.isEmpty()) {\n            uuidPart = UUID.randomUUID().toString();\n        }\n        File destination = new File(\n                watchfaceDirectory,\n                "imported-" + uuidPart.toLowerCase(Locale.ROOT) + ".pbw"\n        );\n        removeOlderImportsWithUuid(parsed.getUuid(), destination);\n'''
    )
    replace_once(
        repository,
        '''    private void copyAndReadBundled(\n''',
        '''    private void removeOlderImportsWithUuid(String uuid, File keep) throws IOException {\n        String[] assetNames = context.getAssets().list(ASSET_DIRECTORY);\n        Set<String> bundledNames = new HashSet<>();\n        if (assetNames != null) {\n            for (String name : assetNames) {\n                bundledNames.add(name);\n            }\n        }\n        File[] files = watchfaceDirectory.listFiles();\n        if (files == null) {\n            return;\n        }\n        for (File file : files) {\n            if (!file.isFile()\n                    || file.equals(keep)\n                    || bundledNames.contains(file.getName())\n                    || !file.getName().toLowerCase(Locale.ROOT).endsWith(".pbw")) {\n                continue;\n            }\n            try (FileInputStream input = new FileInputStream(file)) {\n                WatchfaceMetadata existing = PbwParser.parse(input, file.getName(), false);\n                if (uuid.equalsIgnoreCase(existing.getUuid())) {\n                    file.delete();\n                }\n            } catch (IOException ignored) {\n                // Invalid leftovers do not block importing a valid replacement.\n            }\n        }\n    }\n\n    private void copyAndReadBundled(\n'''
    )

    main_activity = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
    replace_once(main_activity, "import android.app.AlertDialog;\n", "import android.app.AlertDialog;\nimport android.app.Dialog;\n")
    replace_once(main_activity, "import android.graphics.drawable.GradientDrawable;\n", "import android.graphics.drawable.ColorDrawable;\nimport android.graphics.drawable.GradientDrawable;\n")
    replace_once(main_activity, "import android.view.Gravity;\n", "import android.view.Gravity;\nimport android.view.Window;\n")
    replace_once(main_activity, "import android.widget.CheckBox;\n", "")
    schedule_block = r'''private View buildPowerScheduleCard() {
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(14), dp(13), dp(14), dp(13));
    panel.setBackground(panelBackground(
            getColor(R.color.surface),
            getColor(R.color.ink),
            dp(1)
    ));

    TextView title = pixelText(
            "NIGHT MODE BATTERY SAVER",
            14,
            getColor(R.color.text_primary)
    );
    panel.addView(title);

    TextView description = bodyText(
            "Set your Night Mode hours. The watchface stays visible while PebbleOS sleeps "
                    + "in the background to save battery. Charging keeps it running.",
            12,
            getColor(R.color.text_secondary)
    );
    description.setPadding(0, dp(7), 0, dp(9));
    panel.addView(description);

    powerScheduleSummary = pixelText("", 11, getColor(R.color.accent_coral));
    powerScheduleSummary.setPadding(0, 0, 0, dp(11));
    panel.addView(powerScheduleSummary);

    TextView edit = pixelButton(
            "SLEEP SCHEDULE",
            getColor(R.color.paper),
            getColor(R.color.ink)
    );
    edit.setOnClickListener(view -> showPowerScheduleDialog());
    panel.addView(edit, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
    ));
    refreshPowerScheduleSummary();
    return panel;
}

private void refreshPowerScheduleSummary() {
    if (powerScheduleSummary == null || preferences == null) {
        return;
    }
    powerScheduleSummary.setText(
            "SLEEP "
                    + AppPreferences.formatMinutes(preferences.getSleepStartMinutes())
                    + "–"
                    + AppPreferences.formatMinutes(preferences.getSleepEndMinutes())
    );
}

private void showPowerScheduleDialog() {
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(18), dp(17), dp(18), dp(18));
    form.setBackground(panelBackground(
            getColor(R.color.surface),
            getColor(R.color.ink),
            dp(2)
    ));

    TextView title = pixelText("NIGHT MODE", 20, getColor(R.color.text_primary));
    form.addView(title);

    TextView note = bodyText(
            "The current watchface stays on screen. PebbleOS pauses between these times "
                    + "and resumes automatically.",
            12,
            getColor(R.color.text_secondary)
    );
    note.setPadding(0, dp(6), 0, dp(14));
    form.addView(note);

    TextView startLabel = pixelText("START SLEEP", 11, getColor(R.color.accent_coral));
    startLabel.setPadding(0, 0, 0, dp(5));
    form.addView(startLabel);
    EditText start = timeField(AppPreferences.formatMinutes(
            preferences.getSleepStartMinutes()
    ));
    form.addView(start, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
    ));

    TextView endLabel = pixelText("END SLEEP", 11, getColor(R.color.accent_coral));
    endLabel.setPadding(0, dp(12), 0, dp(5));
    form.addView(endLabel);
    EditText end = timeField(AppPreferences.formatMinutes(
            preferences.getSleepEndMinutes()
    ));
    form.addView(end, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
    ));

    TextView format = bodyText(
            "24-HOUR TIME · EXAMPLE 23:30",
            11,
            getColor(R.color.text_muted)
    );
    format.setPadding(0, dp(8), 0, dp(14));
    form.addView(format);

    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);

    TextView cancel = pixelButton(
            "CANCEL",
            getColor(R.color.paper),
            getColor(R.color.ink)
    );
    cancel.setOnClickListener(view -> dialog.dismiss());
    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
    );
    cancelParams.rightMargin = dp(6);
    actions.addView(cancel, cancelParams);

    TextView save = pixelButton(
            "SAVE",
            getColor(R.color.accent_mint),
            getColor(R.color.ink)
    );
    save.setOnClickListener(view -> {
        int startMinutes = parseTime(start.getText().toString());
        int endMinutes = parseTime(end.getText().toString());
        if (startMinutes < 0 || endMinutes < 0) {
            Toast.makeText(
                    this,
                    "Use 24-hour HH:mm format, for example 23:30",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (startMinutes == endMinutes) {
            Toast.makeText(
                    this,
                    "START SLEEP and END SLEEP must be different",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        preferences.setSleepSchedule(startMinutes, endMinutes);
        refreshPowerScheduleSummary();
        PebbleRuntimeService.refreshPowerPolicy(this);
        dialog.dismiss();
    });
    LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
    );
    saveParams.leftMargin = dp(6);
    actions.addView(save, saveParams);
    form.addView(actions);

    dialog.setContentView(form);
    dialog.setCanceledOnTouchOutside(true);
    dialog.show();
    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        window.getDecorView().setPadding(dp(18), 0, dp(18), 0);
    }
}

private EditText timeField(String value) {
    EditText field = new EditText(this);
    field.setSingleLine(true);
    field.setText(value);
    field.setSelectAllOnFocus(true);
    field.setTextSize(18);
    field.setTextColor(getColor(R.color.text_primary));
    field.setTypeface(Typeface.create("monospace", Typeface.BOLD));
    field.setGravity(Gravity.CENTER);
    field.setPadding(dp(10), 0, dp(10), 0);
    field.setBackground(panelBackground(
            getColor(R.color.paper),
            getColor(R.color.ink),
            dp(1)
    ));
    field.setInputType(
            InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME
    );
    return field;
}

private static int parseTime(String value) {
    if (value == null || !value.matches("\\d{1,2}:\\d{2}")) {
        return -1;
    }
    String[] parts = value.split(":", 2);
    try {
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    } catch (NumberFormatException ignored) {
        return -1;
    }
}

'''
    replace_regex(
        main_activity,
        r'''private View buildPowerScheduleCard\(\) \{.*?\n    private View buildReliabilityCard\(\)''',
        schedule_block + "    private View buildReliabilityCard()"
    )
    replace_once(
        main_activity,
        '''        WatchfaceMetadata selected = null;\n''',
        '''        WatchfaceMetadata selected = null;\n        WatchfaceMetadata activeFace = null;\n'''
    )
    replace_once(
        main_activity,
        '''            if (selectedInUi) {\n                selected = watchface;\n            }\n''',
        '''            if (selectedInUi) {\n                selected = watchface;\n            }\n            if (active) {\n                activeFace = watchface;\n            }\n'''
    )
    replace_once(main_activity, "        updateHero(selected);\n", "        updateHero(activeFace != null ? activeFace : selected);\n")
    replace_once(
        main_activity,
        '''            WatchfaceMetadata imported = repository.importFromUri(uri);\n            preferences.setSelectedWatchfaceId(imported.getStorageId());\n''',
        '''            WatchfaceMetadata imported = repository.importFromUri(uri);\n            thumbnails.delete(imported);\n            preferences.setSelectedWatchfaceId(imported.getStorageId());\n'''
    )

    build = "app/build.gradle.kts"
    replace_once(build, "versionCode = 18", "versionCode = 19")
    replace_once(build, 'versionName = "0.8.4"', 'versionName = "0.8.5"')

    print("Applied Pebblehertz 0.8.5 command, thumbnail and Night Mode fixes")


if __name__ == "__main__":
    main()
