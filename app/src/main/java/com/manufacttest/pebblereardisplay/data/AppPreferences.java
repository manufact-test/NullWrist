package com.manufacttest.pebblereardisplay.data;

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

    public boolean isSleepScheduleEnabled() {
        return preferences.getBoolean(KEY_SLEEP_SCHEDULE_ENABLED, false);
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

    public void setSleepSchedule(boolean enabled, int startMinutes, int endMinutes) {
        preferences.edit()
                .putBoolean(KEY_SLEEP_SCHEDULE_ENABLED, enabled)
                .putInt(KEY_SLEEP_START_MINUTES, clampMinutes(startMinutes))
                .putInt(KEY_SLEEP_END_MINUTES, clampMinutes(endMinutes))
                .apply();
    }

    public static String formatMinutes(int minutes) {
        int value = clampMinutes(minutes);
        return String.format(Locale.US, "%02d:%02d", value / 60, value % 60);
    }

    private static int clampMinutes(int minutes) {
        return Math.max(0, Math.min(24 * 60 - 1, minutes));
    }
}
