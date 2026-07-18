package com.manufacttest.pebblereardisplay.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class RuntimePreferences {
    private static final String PREFS_NAME = "pebble_runtime";
    private static final String KEY_WEB_RUNTIME_ENABLED = "web_runtime_enabled";

    private final SharedPreferences preferences;

    public RuntimePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isWebRuntimeEnabled() {
        return preferences.getBoolean(KEY_WEB_RUNTIME_ENABLED, false);
    }

    public void setWebRuntimeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_WEB_RUNTIME_ENABLED, enabled).apply();
    }
}
