package com.manufacttest.pebblereardisplay.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal persistent state required by the always-on runtime. */
public final class AppPreferences {
    private static final String FILE_NAME = "pebble_rear_display";
    private static final String KEY_SELECTED_WATCHFACE = "selected_watchface";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedWatchfaceId() {
        return preferences.getString(KEY_SELECTED_WATCHFACE, null);
    }

    public void setSelectedWatchfaceId(String storageId) {
        if (storageId == null || storageId.isBlank()) {
            clearSelectedWatchfaceId();
            return;
        }
        preferences.edit().putString(KEY_SELECTED_WATCHFACE, storageId).apply();
    }

    public void clearSelectedWatchfaceId() {
        preferences.edit().remove(KEY_SELECTED_WATCHFACE).apply();
    }

    public boolean hasSavedWatchfaceSelection() {
        String value = getSelectedWatchfaceId();
        return value != null && !value.isBlank();
    }
}
