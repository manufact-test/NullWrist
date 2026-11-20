package com.manufact.pixelfoldclosed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The shell base-state override is not guaranteed to persist after reboot. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            context.getSharedPreferences("state", Context.MODE_PRIVATE).edit().putBoolean("requested", false).apply();
    }
}
