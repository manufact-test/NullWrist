package com.manufact.pixelouteronly;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Thin reflection wrapper over the hidden DeviceStateManager API.
 * Requests can outlive this process, so the real state comes from a state callback.
 *
 * Device-state IDs are defined per OEM, so the rear-display state is discovered at runtime
 * (see {@link #detect}) instead of hardcoded. CLOSED is never app-requestable; REAR_DISPLAY
 * (inner screen off, outer screen on) is the state the platform lets the top app request.
 */
// Hidden framework classes/resources are the whole point of this app; see README "How it works".
@SuppressLint({"PrivateApi", "DiscouragedApi"})
final class Fold {
    static final String TAG = "PixelOuterOnly";

    /** Rear-display state ID for this device, or -1 if none was found. */
    static volatile int target = -1;
    /** How {@link #target} was found, for the diagnostics screen. */
    static volatile String source = "not detected";
    /** True while the outer screen is committed; maintained by {@link #watch}. */
    static volatile boolean forcedOuter = false;

    /**
     * Every rear-display-like state ID. Paths can land in different ones (Pixel on Android 17:
     * the hidden request uses 3 REAR_DISPLAY_STATE, WindowExtensions uses 5
     * REAR_DISPLAY_OUTER_DEFAULT), and all of them mean "outer screen".
     */
    static final Set<Integer> rearStates = ConcurrentHashMap.newKeySet();
    /** Which request path last succeeded ("DeviceStateManager" or "WindowExtensions"). */
    static volatile String path = "none yet";
    /** Testing knob: skip the hidden API and use the WindowExtensions path directly. */
    static volatile boolean forceExtensions = false;

    private static boolean detected;
    private static boolean watching;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    static boolean supported(Context ctx) {
        detect(ctx);
        return target >= 0;
    }

    /**
     * Finds the rear-display state, most to least authoritative:
     * 1. the framework config value WindowManager Extensions itself uses,
     * 2. a DeviceState flagged PROPERTY_FEATURE_REAR_DISPLAY (Android 15+),
     * 3. a DeviceState whose name mentions REAR_DISPLAY.
     */
    static synchronized void detect(Context ctx) {
        if (detected) return;
        detected = true;
        List<Object> states = supportedStates(ctx);
        for (Object s : states) {
            if (hasProperty(s, "PROPERTY_FEATURE_REAR_DISPLAY")
                    || name(s).toUpperCase(Locale.ROOT).contains("REAR_DISPLAY")) {
                rearStates.add(id(s));
            }
        }

        int cfg = rearDisplayConfig();
        if (cfg >= 0 && idOf(states, cfg) != null && !hasProperty(idOf(states, cfg), "PROPERTY_APP_INACCESSIBLE")) {
            set(cfg, "config_deviceStateRearDisplay");
            return;
        }
        for (Object s : states) {
            if (hasProperty(s, "PROPERTY_FEATURE_REAR_DISPLAY") && !hasProperty(s, "PROPERTY_APP_INACCESSIBLE")) {
                set(id(s), "PROPERTY_FEATURE_REAR_DISPLAY");
                return;
            }
        }
        for (Object s : states) {
            if (name(s).toUpperCase(Locale.ROOT).contains("REAR_DISPLAY")) {
                set(id(s), "state name " + name(s));
                return;
            }
        }
        Log.w(TAG, "no rear-display state found");
    }

    private static void set(int id, String how) {
        target = id;
        rearStates.add(id);
        source = how;
        Log.i(TAG, "rear-display state " + id + " via " + how);
    }

    /** Human-readable dump for bug reports. */
    static String diagnostics(Context ctx) {
        detect(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("App: ").append(BuildConfig.VERSION_NAME).append('\n');
        sb.append("config_deviceStateRearDisplay: ").append(rearDisplayConfig()).append('\n');
        sb.append("Detected target: ").append(target).append(" (").append(source).append(")\n");
        sb.append("Rear states: ").append(rearStates).append('\n');
        sb.append("Hidden request API: ").append(hiddenRequestAvailable() ? "available" : "MISSING").append('\n');
        sb.append("Window extensions: ").append(RearSession.available() ? "available" : "missing").append('\n');
        sb.append("Last path used: ").append(path).append('\n');
        sb.append("Supported states:\n");
        List<Object> states = supportedStates(ctx);
        if (states.isEmpty()) sb.append("  (none readable)\n");
        for (Object s : states) {
            sb.append("  ").append(id(s)).append(' ').append(name(s));
            if (hasProperty(s, "PROPERTY_FEATURE_REAR_DISPLAY")) sb.append(" [rear]");
            if (hasProperty(s, "PROPERTY_POLICY_AVAILABLE_FOR_APP_REQUEST")) sb.append(" [app-request]");
            if (hasProperty(s, "PROPERTY_APP_INACCESSIBLE")) sb.append(" [app-inaccessible]");
            sb.append('\n');
        }
        return sb.toString();
    }

    // --- state discovery helpers (every call is best-effort; hidden APIs vary by OEM/release) ---

    /** Integer IDs on Android 14, DeviceState objects on 15+. */
    private static List<Object> supportedStates(Context ctx) {
        List<Object> out = new ArrayList<>();
        try {
            Object dsm = dsm(ctx);
            Object r = dsm.getClass().getMethod("getSupportedDeviceStates").invoke(dsm);
            if (r instanceof int[]) {
                for (int i : (int[]) r) out.add(i);
            } else if (r instanceof List) {
                out.addAll((List<?>) r);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getSupportedDeviceStates failed", t);
        }
        return out;
    }

    private static int rearDisplayConfig() {
        try {
            Resources sys = Resources.getSystem();
            int res = sys.getIdentifier("config_deviceStateRearDisplay", "integer", "android");
            return res == 0 ? -1 : sys.getInteger(res);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object idOf(List<Object> states, int id) {
        for (Object s : states) if (id(s) == id) return s;
        return null;
    }

    static int id(Object s) {
        if (s instanceof Integer) return (Integer) s;
        try {
            return (Integer) s.getClass().getMethod("getIdentifier").invoke(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String name(Object s) {
        if (s instanceof Integer) return "";
        try {
            return String.valueOf(s.getClass().getMethod("getName").invoke(s));
        } catch (Throwable t) {
            return "";
        }
    }

    /** Looks the property constant up by name so no OEM/release-specific values are hardcoded. */
    private static boolean hasProperty(Object s, String field) {
        if (s == null || s instanceof Integer) return false;
        try {
            int prop = s.getClass().getField(field).getInt(null);
            return (Boolean) s.getClass().getMethod("hasProperty", int.class).invoke(s, prop);
        } catch (Throwable t) {
            return false;
        }
    }

    // --- requests ---

    /** "device_state" isn't a public Context constant, hence the suppression. */
    @SuppressLint("WrongConstant")
    private static Object dsm(Context ctx) {
        return ctx.getSystemService("device_state");
    }

    /**
     * Keeps forcedOuter in sync with the committed device state; runs onChange (if non-null)
     * on each update. forcedOuter is only ever set here: a request can sit behind the system's
     * "Switch screens?" confirmation, and the user may cancel it.
     */
    static synchronized void watch(Context ctx, Runnable onChange) {
        if (onChange != null && !listeners.contains(onChange)) listeners.add(onChange);
        if (watching) return;
        detect(ctx);
        try {
            Object dsm = dsm(ctx);
            Class<?> cbC = Class.forName(
                    "android.hardware.devicestate.DeviceStateManager$DeviceStateCallback");
            Object cb = Proxy.newProxyInstance(cbC.getClassLoader(), new Class<?>[]{cbC},
                    (proxy, method, args) -> {
                        String n = method.getName();
                        if (n.equals("onDeviceStateChanged") || n.equals("onStateChanged")) {
                            int id = id(args[0]);
                            forcedOuter = id == target || rearStates.contains(id);
                            Log.i(TAG, "device state " + id);
                            for (Runnable r : listeners) r.run();
                        } else if (n.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (n.equals("equals")) {
                            return proxy == args[0];
                        } else if (n.equals("toString")) {
                            return "PixelOuterOnlyCallback";
                        }
                        return null;
                    });
            dsm.getClass().getMethod("registerCallback", Executor.class, cbC)
                    .invoke(dsm, ctx.getMainExecutor(), cb);
            watching = true;
        } catch (Throwable t) {
            Log.e(TAG, "watch failed", t);
        }
    }

    private static void request(Context ctx, int state) throws Exception {
        Object dsm = dsm(ctx);
        Class<?> reqC = Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Class<?> cbC = Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
        Object builder = reqC.getMethod("newBuilder", int.class).invoke(null, state);
        Object req = builder.getClass().getMethod("build").invoke(builder);
        Method m = dsm.getClass().getMethod("requestState", reqC, Executor.class, cbC);
        m.invoke(dsm, req, null, null);
    }

    /** True if this build still lets apps reach DeviceStateRequest.newBuilder(int). */
    static boolean hiddenRequestAvailable() {
        try {
            Class.forName("android.hardware.devicestate.DeviceStateRequest")
                    .getMethod("newBuilder", int.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Hidden API missing or blocked on this build (reflection reports blocked members as absent). */
    private static boolean unreachable(Throwable t) {
        return t instanceof NoSuchMethodException || t instanceof ClassNotFoundException;
    }

    /**
     * Switches to the outer screen. Prefers the hidden request: on Pixel it enters
     * REAR_DISPLAY_STATE, which turns the inner screen off. WindowExtensions is the fallback for
     * builds where the hidden API is unreachable (e.g. Pixel 11 Pro Fold); it is pointed at the
     * same target state, so the inner screen still turns off (see RearSession#retarget).
     */
    static void forceOuter(Activity act) throws Exception {
        if (!forceExtensions) {
            try {
                request(act, target);
                path = "DeviceStateManager";
                Log.i(TAG, "requested state " + target);
                return;
            } catch (Exception e) {
                if (!unreachable(e) || !RearSession.available()) throw e;
                Log.w(TAG, "hidden request API unreachable, using window extensions", e);
            }
        }
        RearSession.start(act, target);
        path = "WindowExtensions";
        Log.i(TAG, "started rear display session");
    }

    static void release(Activity act) throws Exception {
        if (!forceExtensions) {
            try {
                // The active request may belong to an earlier process of ours (from either path);
                // re-request to own it, then cancel (cancelStateRequest only cancels our own).
                request(act, target);
                Object dsm = dsm(act);
                dsm.getClass().getMethod("cancelStateRequest").invoke(dsm);
                path = "DeviceStateManager";
                Log.i(TAG, "cancelled request");
                return;
            } catch (Exception e) {
                if (!unreachable(e) || !RearSession.available()) throw e;
                Log.w(TAG, "hidden request API unreachable, using window extensions", e);
            }
        }
        // endRearDisplaySession silently does nothing for a session started by an earlier
        // process, so take the session over first unless this process owns it.
        if (RearSession.ownsSession()) RearSession.end();
        else RearSession.takeOverAndEnd(act);
        path = "WindowExtensions";
        Log.i(TAG, "ended rear display session");
    }

    private Fold() {}
}
