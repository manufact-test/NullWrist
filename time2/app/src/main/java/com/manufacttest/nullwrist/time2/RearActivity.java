package com.manufacttest.nullwrist.time2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Real PebbleOS on the Titan 2 rear display.
 * Default UI is ONLY the Emery LCD. Four virtual hardware controls are
 * intentionally available solely in an explicit front-launched lab mode.
 */
public final class RearActivity extends Activity {
    public static final String EXTRA_DIAGNOSTICS =
            "com.manufacttest.nullwrist.time2.DIAGNOSTICS";

    private static final int ORANGE=Color.rgb(255,109,0);
    private static final int BG=Color.rgb(8,9,13);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable gestureTimeout=()->{
        if(gestures==null)return;
        gestures.onTimeout(SystemClock.uptimeMillis());
        scheduleGestureTimeout();
    };

    private EmulatorSurfaceView surface;
    private TripleTapSelectRecognizer gestures;
    private EmeryRuntime runtime;
    private TextView diagnosticStatus;
    private boolean diagnostics;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        diagnostics=getIntent().getBooleanExtra(EXTRA_DIAGNOSTICS,false);
        enterImmersive();

        surface=new EmulatorSurfaceView(this);
        surface.setEmptyLabel("STARTING PEBBLEOS");
        surface.setDiagnosticOverlay(diagnostics);
        gestures=new TripleTapSelectRecognizer(new TripleTapSelectRecognizer.Sink(){
            @Override public void touch(TripleTapSelectRecognizer.Type type,int x,int y){
                if(runtime==null||!runtime.isConnected())return;
                runtime.touch(EmeryQmpCommands.TouchPhase.valueOf(type.name()),x,y);
            }
            @Override public void select(){
                if(runtime!=null&&runtime.isConnected())
                    runtime.press(EmeryQmpCommands.Button.SELECT);
            }
        });
        surface.setInputListener((type,x,y)->{
            if(runtime==null||!runtime.isConnected()){
                gestures.reset();
                handler.removeCallbacks(gestureTimeout);
                return;
            }
            gestures.onEvent(TripleTapSelectRecognizer.Type.valueOf(type.name()),
                    x,y,SystemClock.uptimeMillis());
            scheduleGestureTimeout();
        });

        if(diagnostics)showDiagnosticShell();
        else setContentView(surface);

        if(!EmeryAssets.isBundled(this)){
            surface.setEmptyLabel("PEBBLEOS UNAVAILABLE");
            if(diagnosticStatus!=null)
                diagnosticStatus.setText("Emery firmware/QEMU not bundled in this APK");
            return;
        }
        runtime=new EmeryRuntime(this,new EmeryRuntime.Listener(){
            @Override public void status(String message){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(diagnosticStatus!=null)diagnosticStatus.setText(message);
                    if(message.startsWith("Emery start failed") ||
                            message.startsWith("No Emery LCD") ||
                            message.startsWith("Another Emery") ||
                            message.startsWith("Emery QEMU stopped")) {
                        surface.setEmptyLabel("PEBBLEOS ERROR — SEE DIAGNOSTICS");
                        gestures.reset();
                        handler.removeCallbacks(gestureTimeout);
                    }
                });
            }
            @Override public void frame(Bitmap image){
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())surface.setFrame(image);
                });
            }
        });
        runtime.start();
    }

    /** Only the advanced diagnostic mode shows any Android buttons. */
    private void showDiagnosticShell() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView title=label("TIME 2 / DIAGNOSTICS",11);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        diagnosticStatus=label("Waiting for genuine PebbleOS frame",10);
        diagnosticStatus.setGravity(Gravity.CENTER);
        root.addView(diagnosticStatus,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout controls=new LinearLayout(this);
        for(EmeryQmpCommands.Button key:EmeryQmpCommands.Button.values()){
            Button button=new Button(this);
            button.setText(key==EmeryQmpCommands.Button.SELECT?"OK":key.name());
            button.setAllCaps(false);
            button.setTextSize(11);
            button.setTextColor(BG);
            button.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            GradientDrawable background=new GradientDrawable();
            background.setColor(ORANGE);
            button.setBackground(background);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);
            params.setMargins(dp(2),0,dp(2),0);
            controls.addView(button,params);
            button.setOnClickListener(view->{
                if(runtime!=null&&runtime.isConnected())runtime.press(key);
            });
        }
        root.addView(controls);
        setContentView(root);
    }

    private void scheduleGestureTimeout(){
        handler.removeCallbacks(gestureTimeout);
        if(gestures==null)return;
        long next=gestures.nextDeadlineMs();
        if(next>=0)handler.postDelayed(gestureTimeout,
                Math.max(1,next-SystemClock.uptimeMillis()));
    }

    private void enterImmersive(){
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                |View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)enterImmersive();
    }

    @Override protected void onPause(){
        handler.removeCallbacks(gestureTimeout);
        if(gestures!=null)gestures.reset();
        super.onPause();
    }

    @Override protected void onDestroy(){
        handler.removeCallbacks(gestureTimeout);
        if(gestures!=null)gestures.reset();
        if(runtime!=null)runtime.close();
        super.onDestroy();
    }

    private TextView label(String message,int size){
        TextView view=new TextView(this);
        view.setText(message);
        view.setTextColor(ORANGE);
        view.setTextSize(size);
        view.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        return view;
    }
    private int dp(float x){
        return (int)(x*getResources().getDisplayMetrics().density+.5f);
    }
}
