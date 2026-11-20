package com.manufact.pixelouteronly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.service.quicksettings.TileService;

/**
 * Always-on notification: a one-tap toggle from either screen. Also keeps the process
 * (and therefore any active state request) alive.
 */
public class HoldService extends Service {
    private static final String CH = "hold";
    private final Runnable postOnChange = this::post; // one instance, so watch() dedupes it

    /** Starts the service only on devices with a requestable rear-display state. */
    static void start(Context ctx) {
        if (Fold.supported(ctx)) ctx.startForegroundService(new Intent(ctx, HoldService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        post(); // startForeground must happen promptly, even if we then stop
        if (!Fold.supported(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Fold.watch(this, postOnChange);
        return START_STICKY;
    }

    private void post() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
        PendingIntent tap = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        boolean outer = Fold.forcedOuter;
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_fold)
                .setContentTitle(getString(outer ? R.string.notif_outer_title : R.string.notif_inner_title))
                .setContentText(getString(outer ? R.string.notif_outer_text : R.string.notif_inner_text))
                .setContentIntent(tap)
                .setOngoing(true)
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        TileService.requestListeningState(this, new ComponentName(this, ToggleTile.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
