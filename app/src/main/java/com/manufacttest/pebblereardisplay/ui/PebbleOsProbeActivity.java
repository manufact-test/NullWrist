package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.os.Bundle;

/** Internal compatibility wrapper retained for old debug intents. */
public final class PebbleOsProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new PebbleOsSurfaceView(this));
    }
}
