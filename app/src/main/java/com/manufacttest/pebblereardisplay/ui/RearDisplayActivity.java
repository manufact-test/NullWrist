package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;

public final class RearDisplayActivity extends Activity {
    private boolean previewMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        previewMode = getIntent().getBooleanExtra(DisplayUtils.EXTRA_PREVIEW_MODE, false);

        try {
            setContentView(new RearClockView(
                    this,
                    new WatchfaceRepository(this),
                    new AppPreferences(this)
            ));
        } catch (RuntimeException exception) {
            TextView fallback = new TextView(this);
            fallback.setBackgroundColor(Color.BLACK);
            fallback.setTextColor(Color.WHITE);
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setText("Rear display could not be initialized\n" + exception.getClass().getSimpleName());
            setContentView(fallback);
        }

        getWindow().getDecorView().post(() -> RearUi.enterImmersive(this));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void onBackPressed() {
        if (previewMode) {
            finish();
        }
        // Back remains ignored on the physical passive rear-display surface.
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            RearUi.enterImmersive(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (previewMode) {
            RearUi.leaveImmersive(this);
        }
        super.onDestroy();
    }
}