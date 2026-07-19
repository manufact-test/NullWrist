package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;

public final class PebbleFramebufferView extends View {
    private static final long ACTIVE_POLL_MILLIS = 50;
    private static final long IDLE_POLL_MILLIS = 250;
    private static final int IDLE_AFTER_UNCHANGED_POLLS = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint();
    private final Bitmap bitmap = Bitmap.createBitmap(
            PebbleQemuProcess.WIDTH,
            PebbleQemuProcess.HEIGHT,
            Bitmap.Config.ARGB_8888
    );
    private final int[] pixels = new int[PebbleQemuProcess.WIDTH * PebbleQemuProcess.HEIGHT];
    private PebbleQemuProcess runtime;
    private boolean polling;
    private int lastSequence = -1;
    private int unchangedPolls;

    private final Runnable framePoll = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }

            long nextDelay = IDLE_POLL_MILLIS;
            PebbleQemuProcess current = runtime;
            if (current != null) {
                int sequence = current.readFrameSequence();
                if (sequence > 0 && sequence != lastSequence) {
                    if (current.readFrame(pixels)) {
                        bitmap.setPixels(
                                pixels,
                                0,
                                PebbleQemuProcess.WIDTH,
                                0,
                                0,
                                PebbleQemuProcess.WIDTH,
                                PebbleQemuProcess.HEIGHT
                        );
                        lastSequence = sequence;
                        unchangedPolls = 0;
                        invalidate();
                    }
                    nextDelay = ACTIVE_POLL_MILLIS;
                } else if (sequence > 0) {
                    unchangedPolls++;
                    nextDelay = unchangedPolls < IDLE_AFTER_UNCHANGED_POLLS
                            ? ACTIVE_POLL_MILLIS
                            : IDLE_POLL_MILLIS;
                } else {
                    unchangedPolls = 0;
                    nextDelay = ACTIVE_POLL_MILLIS;
                }
            }
            handler.postDelayed(this, nextDelay);
        }
    };

    public PebbleFramebufferView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);
    }

    public void attach(PebbleQemuProcess runtime) {
        this.runtime = runtime;
        lastSequence = -1;
        unchangedPolls = 0;
        if (!polling) {
            polling = true;
            handler.post(framePoll);
        }
    }

    public void detach() {
        polling = false;
        handler.removeCallbacks(framePoll);
        runtime = null;
        lastSequence = -1;
        unchangedPolls = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableWidth = getWidth();
        int availableHeight = getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        float scale = Math.min(
                availableWidth / (float) PebbleQemuProcess.WIDTH,
                availableHeight / (float) PebbleQemuProcess.HEIGHT
        );
        int drawnWidth = Math.max(1, Math.round(PebbleQemuProcess.WIDTH * scale));
        int drawnHeight = Math.max(1, Math.round(PebbleQemuProcess.HEIGHT * scale));
        int left = (availableWidth - drawnWidth) / 2;
        int top = (availableHeight - drawnHeight) / 2;
        Rect destination = new Rect(left, top, left + drawnWidth, top + drawnHeight);
        canvas.drawBitmap(bitmap, null, destination, paint);
    }
}
