package com.manufacttest.pebblereardisplay.runtime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Cancels a task-removal fallback when the user leaves Reliable mode. */
public final class RuntimeRecoveryScheduler {
    private static final String ACTION_RECOVER_TASK =
            "com.manufacttest.pebblereardisplay.action.RECOVER_AFTER_TASK_REMOVAL";
    private static final int RECOVERY_REQUEST_CODE = 810;

    private RuntimeRecoveryScheduler() {
    }

    public static void cancel(Context context) {
        Context application = context.getApplicationContext();
        PendingIntent pending = PendingIntent.getForegroundService(
                application,
                RECOVERY_REQUEST_CODE,
                new Intent(application, PebbleRuntimeService.class).setAction(ACTION_RECOVER_TASK),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pending == null) {
            return;
        }
        AlarmManager alarm = application.getSystemService(AlarmManager.class);
        if (alarm != null) {
            alarm.cancel(pending);
        }
        pending.cancel();
    }
}
