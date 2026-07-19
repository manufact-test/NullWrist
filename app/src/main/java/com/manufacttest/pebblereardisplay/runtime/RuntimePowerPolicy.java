package com.manufacttest.pebblereardisplay.runtime;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.manufacttest.pebblereardisplay.data.AppPreferences;

import java.time.LocalTime;

public final class RuntimePowerPolicy {
    public static final int LOW_BATTERY_PERCENT = 15;

    private RuntimePowerPolicy() {}

    public static Snapshot evaluate(Context context, AppPreferences preferences) {
        BatteryState battery = readBattery(context);
        if (battery.charging) {
            return new Snapshot(Mode.RUNNING, battery.percent, true);
        }

        LocalTime now = LocalTime.now();
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        if (preferences.isSleepScheduleEnabled()
                && isInsideSchedule(
                minuteOfDay,
                preferences.getSleepStartMinutes(),
                preferences.getSleepEndMinutes()
        )) {
            return new Snapshot(Mode.SCHEDULED_FREEZE, battery.percent, false);
        }

        if (battery.percent >= 0 && battery.percent < LOW_BATTERY_PERCENT) {
            return new Snapshot(Mode.LOW_BATTERY_PULSE, battery.percent, false);
        }
        return new Snapshot(Mode.RUNNING, battery.percent, false);
    }

    static boolean isInsideSchedule(int minuteOfDay, int startMinutes, int endMinutes) {
        int current = normalize(minuteOfDay);
        int start = normalize(startMinutes);
        int end = normalize(endMinutes);
        if (start == end) {
            return false;
        }
        if (start < end) {
            return current >= start && current < end;
        }
        return current >= start || current < end;
    }

    private static BatteryState readBattery(Context context) {
        Intent battery = context.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        if (battery == null) {
            return new BatteryState(-1, false);
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || plugged != 0;
        int percent = level >= 0 && scale > 0
                ? Math.round(level * 100f / scale)
                : -1;
        return new BatteryState(percent, charging);
    }

    private static int normalize(int minutes) {
        return Math.floorMod(minutes, 24 * 60);
    }

    public enum Mode {
        RUNNING,
        SCHEDULED_FREEZE,
        LOW_BATTERY_PULSE
    }

    public static final class Snapshot {
        public final Mode mode;
        public final int batteryPercent;
        public final boolean charging;

        Snapshot(Mode mode, int batteryPercent, boolean charging) {
            this.mode = mode;
            this.batteryPercent = batteryPercent;
            this.charging = charging;
        }
    }

    private static final class BatteryState {
        final int percent;
        final boolean charging;

        BatteryState(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }
}
