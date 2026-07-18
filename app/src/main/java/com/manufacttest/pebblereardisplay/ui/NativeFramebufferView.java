package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import com.manufacttest.pebblereardisplay.runtime.PebbleNativeRuntime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class NativeFramebufferView extends View {
    private static final long FRAME_DELAY_MS = 100L;

    private final Paint paint = new Paint();
    private final Bitmap bitmap;
    private final ByteBuffer framebuffer;
    private final Rect source;
    private final Rect destination = new Rect();
    private long frameNumber;
    private boolean running;

    NativeFramebufferView(Context context) {
        super(context);
        int width = PebbleNativeRuntime.frameWidth();
        int height = PebbleNativeRuntime.frameHeight();
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        framebuffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder());
        source = new Rect(0, 0, width, height);
        paint.setFilterBitmap(false);
        setBackgroundColor(Color.BLACK);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) {
            return;
        }

        framebuffer.clear();
        if (PebbleNativeRuntime.fillTestFrame(framebuffer, frameNumber++)) {
            framebuffer.position(0);
            bitmap.copyPixelsFromBuffer(framebuffer);
        }

        int availableWidth = getWidth();
        int availableHeight = getHeight();
        float scale = Math.min(
                availableWidth / (float) source.width(),
                availableHeight / (float) source.height()
        );
        int drawWidth = Math.max(1, Math.round(source.width() * scale));
        int drawHeight = Math.max(1, Math.round(source.height() * scale));
        int left = (availableWidth - drawWidth) / 2;
        int top = (availableHeight - drawHeight) / 2;
        destination.set(left, top, left + drawWidth, top + drawHeight);
        canvas.drawBitmap(bitmap, source, destination, paint);

        postInvalidateDelayed(FRAME_DELAY_MS);
    }
}
