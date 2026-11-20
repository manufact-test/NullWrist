package com.manufact.pixelouteronly;

import android.app.Activity;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Fallback path through the OEM WindowManager Extensions library (androidx.window.extensions),
 * the same rear-display implementation Jetpack WindowManager uses. It ships on the device as a
 * platform library, so it keeps working on builds where the hidden DeviceStateRequest API is
 * no longer reachable from apps. Declared optional in the manifest via uses-library.
 */
final class RearSession {
    private static Object component; // androidx.window.extensions.area.WindowAreaComponent
    private static boolean looked;
    /** True while a session started by this process is active (per the session callback). */
    private static volatile boolean owned;
    /** Set by takeOverAndEnd: end the session as soon as it reports active. */
    private static volatile boolean endWhenActive;

    static boolean ownsSession() {
        return owned;
    }

    /** The WindowAreaComponent, or null if this device doesn't ship one. */
    static synchronized Object component() {
        if (looked) return component;
        looked = true;
        try {
            Object ext = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
                    .getMethod("getWindowExtensions").invoke(null);
            component = ext.getClass().getMethod("getWindowAreaComponent").invoke(ext);
        } catch (Throwable t) {
            Log.i(Fold.TAG, "window extensions unavailable: " + t);
        }
        return component;
    }

    /** The state the library picks itself (5 on Android 17 Pixels), or -1 if unknown. */
    private static int defaultState = -1;

    /**
     * Points the library at a specific rear-display state before a session starts.
     * WindowAreaComponentImpl keeps the state it requests in a plain field of its own
     * (non-framework) class; on Android 17 Pixels it defaults to REAR_DISPLAY_OUTER_DEFAULT (5),
     * which leaves the inner screen on. REAR_DISPLAY_STATE (3) turns it off. The library is
     * platform code, so its own DeviceStateRequest call isn't blocked like ours.
     * Returns false if this OEM's implementation has no such field (library default is used).
     */
    private static boolean retarget(Object c, int state) {
        if (state < 0) return false;
        try {
            Field f = c.getClass().getDeclaredField("mRearDisplayState");
            if (f.getType() != int.class) return false;
            f.setAccessible(true);
            if (defaultState < 0) defaultState = f.getInt(c);
            f.setInt(c, state);
            return true;
        } catch (Throwable t) {
            Log.i(Fold.TAG, "rear state field unavailable: " + t);
            return false;
        }
    }

    static boolean available() {
        return component() != null;
    }

    /** Starts a session in {@code state} if the library allows retargeting, else its default. */
    static void start(Activity activity, int state) throws Exception {
        endWhenActive = false; // a user-requested session must never inherit a pending takeover end
        startSession(activity, state);
    }

    private static void startSession(Activity activity, int state) throws Exception {
        Object c = component();
        if (c == null) throw new IllegalStateException("window extensions unavailable");
        if (retarget(c, state)) Log.i(Fold.TAG, "rear session retargeted to state " + state);
        Method start = find(c, "startRearDisplaySession");
        // The session callback type differs by vendor API level (java.util.function.Consumer
        // vs androidx.window.extensions.core.util.function.Consumer), so proxy whichever it is.
        Class<?> cbType = start.getParameterTypes()[1];
        Object cb = Proxy.newProxyInstance(cbType.getClassLoader(), new Class<?>[]{cbType},
                (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "accept":
                            // SESSION_STATE_INACTIVE = 0; ACTIVE (1) / CONTENT_VISIBLE (2) = ours.
                            owned = !Integer.valueOf(0).equals(args[0]);
                            Log.i(Fold.TAG, "rear session status " + args[0]);
                            if (owned && endWhenActive) {
                                endWhenActive = false;
                                try {
                                    end();
                                    Log.i(Fold.TAG, "takeover session ended on activation");
                                } catch (Exception e) {
                                    Log.w(Fold.TAG, "takeover end failed", e);
                                }
                            }
                            return null;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "PixelOuterOnlyRearSession";
                        default: return null;
                    }
                });
        start.invoke(c, activity, cb);
    }

    /**
     * Takes over a session left by an earlier process and ends it. Uses the library's default
     * state for the takeover because entering REAR_DISPLAY_STATE shows a confirmation prompt.
     * Some OEMs (Samsung) activate sessions asynchronously and ignore an end() that arrives
     * first, so the end is repeated from the session callback once it reports active.
     */
    static void takeOverAndEnd(Activity activity) throws Exception {
        Object c = component();
        if (c == null) throw new IllegalStateException("window extensions unavailable");
        startSession(activity, defaultState);
        endWhenActive = true;
        end(); // enough on synchronous OEMs (Pixel); the callback repeats it for async ones
        if (owned) endWhenActive = false;
    }

    static void end() throws Exception {
        Object c = component();
        if (c == null) throw new IllegalStateException("window extensions unavailable");
        c.getClass().getMethod("endRearDisplaySession").invoke(c);
    }

    private static Method find(Object c, String name) throws NoSuchMethodException {
        for (Method m : c.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 2) return m;
        }
        throw new NoSuchMethodException(c.getClass().getName() + "." + name);
    }

    private RearSession() {}
}
