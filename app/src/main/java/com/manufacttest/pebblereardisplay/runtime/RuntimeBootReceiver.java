package com.manufacttest.pebblereardisplay.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.manufacttest.pebblereardisplay.data.AppPreferences;

/** Restores the selected rear watchface after reboot or an in-place app update. */
public final class RuntimeBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (!new AppPreferences(context).hasSavedWatchfaceSelection()) {
            return;
        }
        try {
            PebbleRuntimeService.start(context);
        } catch (RuntimeException ignored) {
            // Activity startup and the runtime watchdog remain as fallback paths.
        }
    }
}
