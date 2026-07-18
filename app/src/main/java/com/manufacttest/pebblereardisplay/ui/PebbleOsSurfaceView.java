package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;
import com.manufacttest.pebblereardisplay.runtime.PebbleRuntimeService;

/** Passive PebbleOS renderer. The runtime itself is owned by PebbleRuntimeService. */
public final class PebbleOsSurfaceView extends FrameLayout {
    private final PebbleFramebufferView framebufferView;
    private final TextView statusView;
    private PebbleQemuProcess attachedRuntime;
    private boolean listening;

    private final PebbleRuntimeService.Listener runtimeListener = (runtime, status, failure) -> post(() -> {
        if (!listening) {
            return;
        }
        if (runtime != attachedRuntime) {
            framebufferView.detach();
            attachedRuntime = runtime;
            if (runtime != null) {
                framebufferView.attach(runtime);
            }
        }

        if (failure != null) {
            statusView.setText("Could not start selected watchface\n" + failure);
            statusView.setVisibility(View.VISIBLE);
        } else if (status != null && !status.isEmpty()) {
            statusView.setText(status);
            statusView.setVisibility(View.VISIBLE);
        } else {
            statusView.setVisibility(View.GONE);
        }
    });

    public PebbleOsSurfaceView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setClickable(false);
        setFocusable(false);

        framebufferView = new PebbleFramebufferView(context);
        addView(framebufferView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        statusView = new TextView(context);
        statusView.setText("Starting PebbleOS…");
        statusView.setTextColor(Color.WHITE);
        statusView.setShadowLayer(5f, 0f, 1f, Color.BLACK);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(24), dp(24), dp(24), dp(24));
        addView(statusView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!listening) {
            listening = true;
            PebbleRuntimeService.addListener(runtimeListener);
        }
        PebbleRuntimeService.start(getContext());
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    public void release() {
        if (listening) {
            listening = false;
            PebbleRuntimeService.removeListener(runtimeListener);
        }
        framebufferView.detach();
        attachedRuntime = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
