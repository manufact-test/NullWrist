package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public final class RearDisplayActivity extends Activity {
    private boolean previewMode;
    private View rearSurface;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        previewMode = getIntent().getBooleanExtra(DisplayUtils.EXTRA_PREVIEW_MODE, false);

        try {
            rearSurface = new PebbleOsSurfaceView(this);
        } catch (RuntimeException exception) {
            TextView fallback = new TextView(this);
            fallback.setBackgroundColor(Color.BLACK);
            fallback.setTextColor(Color.WHITE);
            fallback.setGravity(Gravity.CENTER);
            fallback.setText("Rear display could not be initialized\n"
                    + exception.getClass().getSimpleName());
            rearSurface = fallback;
        }

        View content = rearSurface;
        if (previewMode) {
            FrameLayout preview = new FrameLayout(this);
            preview.setBackgroundColor(Color.BLACK);
            preview.addView(rearSurface, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            TextView hint = new TextView(this);
            hint.setText("PRESS THE PHONE'S BACK KEY TO EXIT PREVIEW");
            hint.setTextColor(0xffffd84d);
            hint.setTextSize(11);
            hint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(12), dp(9), dp(12), dp(9));
            GradientDrawable hintBackground = new GradientDrawable();
            hintBackground.setColor(0xe61b1b1b);
            hintBackground.setStroke(dp(1), 0xffffd84d);
            hintBackground.setCornerRadius(dp(2));
            hint.setBackground(hintBackground);

            FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            hintParams.setMargins(dp(12), dp(12), dp(12), dp(12));
            preview.addView(hint, hintParams);
            content = preview;
        }

        setContentView(content);
        rearSurface.setClickable(true);
        rearSurface.setFocusable(true);
        rearSurface.setFocusableInTouchMode(true);
        rearSurface.requestFocus();
        registerBackGuard();
        rearSurface.post(() -> RearUi.lockRearSurface(this, rearSurface));
    }

    private void registerBackGuard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        backCallback = () -> {
            if (previewMode) {
                finish();
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                backCallback
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rearSurface != null) {
            RearUi.lockRearSurface(this, rearSurface);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (previewMode && keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (previewMode) {
            finish();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        RearUi.enterImmersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && rearSurface != null) {
            RearUi.lockRearSurface(this, rearSurface);
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        if (previewMode) {
            RearUi.leaveImmersive(this);
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
