package com.manufacttest.nullwrist.time2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * One durable owner for the native QEMU process, independent of the rear
 * Activity. Runs only following explicit user launch and shows a foreground
 * notification with a STOP action; does not start itself at boot.
 */
public final class EmeryEmulatorService extends Service {
    public static final String ACTION_START="com.manufacttest.nullwrist.time2.EMERY_START";
    public static final String ACTION_STOP="com.manufacttest.nullwrist.time2.EMERY_STOP";
    private static final String CHANNEL="emery-runtime";
    private static final int NOTIFICATION_ID=2452;
    private final CopyOnWriteArraySet<EmeryRuntime.Listener> listeners=
            new CopyOnWriteArraySet<>();
    private final IBinder binder=new LocalBinder();
    private EmeryRuntime runtime;
    private volatile Bitmap lastFrame;
    private volatile String lastStatus="Preparing Emery session";

    public final class LocalBinder extends Binder{
        public EmeryEmulatorService getService(){return EmeryEmulatorService.this;}
    }

    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,
                "PebbleOS Emulator",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("One local Emery guest, even while switching screens");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){
            if(runtime==null){stopSelf();return START_NOT_STICKY;}
            // Never abruptly SIGTERM a live game when it could persist state
            // in its app DEINIT callback. The single serial owner sends STOP.
            android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] completed={false};
            Runnable stop=()->main.post(()->{
                if(completed[0])return;
                completed[0]=true;
                stopSelf();
            });
            runtime.requestGracefulStop(stop);
            // Failsafe for blocked/failed guest protocol. Do not leave an
            // immortal foreground service if QEMU no longer responds.
            main.postDelayed(stop,30000);
            return START_NOT_STICKY;
        }
        Intent launch=new Intent(this,MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(this,1,launch,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent=new Intent(this,EmeryEmulatorService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop=PendingIntent.getService(this,2,stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_nullwrist_time2)
                .setContentTitle("NullWrist Time 2 · PebbleOS")
                .setContentText("Emery is running on Titan 2")
                .setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null,"STOP",stop).build())
                .build();
        startForeground(NOTIFICATION_ID,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if(runtime==null){
            runtime=new EmeryRuntime(getApplicationContext(),new EmeryRuntime.Listener(){
                @Override public void status(String message){
                    lastStatus=message;
                    for(EmeryRuntime.Listener l:listeners)l.status(message);
                }
                @Override public void frame(Bitmap bitmap){
                    lastFrame=bitmap;
                    for(EmeryRuntime.Listener l:listeners)l.frame(bitmap);
                }
            });
            runtime.start();
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent){return binder;}

    public void addListener(EmeryRuntime.Listener listener){
        listeners.add(listener);
        listener.status(lastStatus);
        Bitmap frame=lastFrame;
        if(frame!=null)listener.frame(frame);
    }
    public void removeListener(EmeryRuntime.Listener listener){listeners.remove(listener);}
    public Bitmap getLastFrame(){return lastFrame;}
    public boolean isConnected(){return runtime!=null && runtime.isConnected();}
    public void requestPendingOperation(){
        // The service's single serial owner polls durable pending preferences.
    }
    public void touch(EmeryQmpCommands.TouchPhase phase,int x,int y){
        if(runtime!=null)runtime.touch(phase,x,y);
    }
    public void press(EmeryQmpCommands.Button button){
        if(runtime!=null)runtime.press(button);
    }
    @Override public void onDestroy(){
        listeners.clear();
        if(runtime!=null){runtime.close();runtime=null;}
        super.onDestroy();
    }
}
