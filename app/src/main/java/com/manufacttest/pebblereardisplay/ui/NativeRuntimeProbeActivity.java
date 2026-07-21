package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.manufacttest.pebblereardisplay.runtime.PebbleNativeRuntime;

public final class NativeRuntimeProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (!PebbleNativeRuntime.isLoaded()) {
            status.setText("NATIVE LOAD FAILED\n" + PebbleNativeRuntime.loadError());
            setContentView(root);
            return;
        }

        boolean selfTest = PebbleNativeRuntime.selfTest();
        status.setText((selfTest ? "NATIVE BRIDGE OK" : "NATIVE SELF-TEST FAILED")
                + "\n" + PebbleNativeRuntime.buildInfo()
                + "\nAnimated pixels below are generated in C++ and copied through JNI.");

        NativeFramebufferView framebuffer = new NativeFramebufferView(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(framebuffer, frameParams);
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
