package com.manufacttest.pebblereardisplay.runtime;

import java.nio.ByteBuffer;

public final class PebbleNativeRuntime {
    private static final Throwable LOAD_ERROR;

    static {
        Throwable error = null;
        try {
            System.loadLibrary("pebble_runtime");
        } catch (Throwable throwable) {
            error = throwable;
        }
        LOAD_ERROR = error;
    }

    private PebbleNativeRuntime() {}

    public static boolean isLoaded() {
        return LOAD_ERROR == null;
    }

    public static String loadError() {
        return LOAD_ERROR == null
                ? ""
                : LOAD_ERROR.getClass().getSimpleName() + ": " + LOAD_ERROR.getMessage();
    }

    public static String buildInfo() {
        ensureLoaded();
        return nativeBuildInfo();
    }

    public static boolean selfTest() {
        ensureLoaded();
        return nativeSelfTest();
    }

    public static int frameWidth() {
        ensureLoaded();
        return nativeFrameWidth();
    }

    public static int frameHeight() {
        ensureLoaded();
        return nativeFrameHeight();
    }

    public static boolean fillTestFrame(ByteBuffer buffer, long frameNumber) {
        ensureLoaded();
        if (buffer == null || !buffer.isDirect()) {
            throw new IllegalArgumentException("Framebuffer must be a direct ByteBuffer");
        }
        return nativeFillTestFrame(buffer, frameNumber);
    }

    private static void ensureLoaded() {
        if (LOAD_ERROR != null) {
            throw new IllegalStateException("Native library failed to load", LOAD_ERROR);
        }
    }

    private static native String nativeBuildInfo();
    private static native boolean nativeSelfTest();
    private static native int nativeFrameWidth();
    private static native int nativeFrameHeight();
    private static native boolean nativeFillTestFrame(ByteBuffer buffer, long frameNumber);
}
