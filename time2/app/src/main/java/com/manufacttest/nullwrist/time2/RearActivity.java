package com.manufacttest.nullwrist.time2;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/** Touch/coordinate-probe UI, isolated from the foreground host Android app. */
public final class RearActivity extends Activity {
    private static final int ORANGE=Color.rgb(255,109,0);
    private static final int BG=Color.rgb(8,9,13);
    private TextView output;
    private TextView footer;
    private EmeryRuntime runtime;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8),dp(10),dp(8),dp(10));

        PixelWordmarkView wordmark=new PixelWordmarkView(this);
        root.addView(wordmark,new LinearLayout.LayoutParams(-1,dp(26)));
        TextView top=label("TIME 2  /  EMERY LAB",11);
        top.setGravity(Gravity.CENTER);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(22)));

        EmulatorSurfaceView surface=new EmulatorSurfaceView(this);
        LinearLayout.LayoutParams surfaceParams=new LinearLayout.LayoutParams(-1,0,1);
        root.addView(surface,surfaceParams);

        output=label("TAP THE DISPLAY TO CHECK 200x228 INPUT",10);
        output.setGravity(Gravity.CENTER);
        root.addView(output,new LinearLayout.LayoutParams(-1,dp(30)));
        surface.setInputListener((type,x,y)->{
            if(runtime!=null && runtime.isConnected()){
                EmeryQmpCommands.TouchPhase phase =
                    EmeryQmpCommands.TouchPhase.valueOf(type.name());
                runtime.touch(phase,x,y);
            }else{
                output.setText(type.name()+" "+x+":"+y+" [INPUT PROBE]");
            }
        });

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        for(String name:new String[]{"BACK","UP","OK","DOWN"}) {
            Button button=new Button(this);
            button.setText(name); button.setAllCaps(false); button.setTextSize(10);
            button.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            button.setTextColor(BG);
            GradientDrawable background=new GradientDrawable();
            background.setColor(ORANGE); background.setCornerRadius(0);
            button.setBackground(background);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(40),1);
            params.setMargins(dp(2),0,dp(2),0);
            controls.addView(button,params);
            button.setOnClickListener(view->{
                EmeryQmpCommands.Button key = "OK".equals(name)
                    ? EmeryQmpCommands.Button.SELECT
                    : EmeryQmpCommands.Button.valueOf(name);
                if(runtime!=null && runtime.isConnected()){
                    runtime.press(key);
                    output.setText(name+" [QMP COMMAND]");
                }else output.setText(name+" [INPUT PROBE / NO VM]");
            });
        }
        root.addView(controls);
        footer=label("NOT AN ACTIVE PEBBLEOS SESSION",9);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(25)));
        setContentView(root);
        if(EmeryAssets.isBundled(this)){
            runtime=new EmeryRuntime(this,new EmeryRuntime.Listener(){
                @Override public void status(String message){
                    runOnUiThread(()->{
                        if(isFinishing()||isDestroyed())return;
                        output.setText(message);
                        footer.setText("EMERY LAB / REAL FIRMWARE TEST");
                    });
                }
                @Override public void frame(android.graphics.Bitmap image){
                    runOnUiThread(()->{
                        if(!isFinishing()&&!isDestroyed())surface.setFrame(image);
                    });
                }
            });
            runtime.start();
        }else{
            output.setText("NO FIRMWARE/QEMU ASSETS — INPUT PROBE ONLY");
        }
    }

    @Override protected void onDestroy(){
        if(runtime!=null)runtime.close();
        super.onDestroy();
    }

    private TextView label(String message,int size) {
        TextView view=new TextView(this);
        view.setText(message);
        view.setTextColor(ORANGE);
        view.setTextSize(size);
        view.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        return view;
    }
    private int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
}
