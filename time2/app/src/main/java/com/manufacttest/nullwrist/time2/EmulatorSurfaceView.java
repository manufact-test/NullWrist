package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Platform-independent frame presentation and strict coordinate mapping.
 * Today renders an honest 'core unavailable' placeholder. Later a versioned
 * EmulatorBackend can deliver actual 200x228 frames and receive events.
 */
public final class EmulatorSurfaceView extends View {
    private static final int ORANGE = Color.rgb(255, 109, 0);
    private static final int BG = Color.rgb(8, 9, 13);
    private final Paint paint = new Paint();
    private Bitmap frame;
    private ViewportMapper mapper;
    private InputListener listener;
    private ViewportMapper.Point lastTouch;
    private boolean pointerDown;

    public EmulatorSurfaceView(Context context) {
        super(context);
        paint.setAntiAlias(false);
        setBackgroundColor(BG);
    }

    /** Frames must match the emulated LCD exactly, not Android dimensions. */
    public void setFrame(Bitmap frame) {
        if (frame != null && (frame.getWidth() != ViewportMapper.EMERY_WIDTH
                || frame.getHeight() != ViewportMapper.EMERY_HEIGHT)) {
            throw new IllegalArgumentException("Emery frames must be 200x228");
        }
        this.frame = frame;
        invalidate();
    }

    public void setInputListener(InputListener listener) { this.listener = listener; }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        mapper = w > 0 && h > 0
                ? ViewportMapper.fit(ViewportMapper.EMERY_WIDTH, ViewportMapper.EMERY_HEIGHT, w, h)
                : null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mapper == null) return;
        RectF lcd = new RectF(mapper.left, mapper.top, mapper.right, mapper.bottom);
        paint.setColor(Color.rgb(20, 20, 24));
        canvas.drawRect(lcd, paint);
        if (frame != null) {
            paint.setFilterBitmap(false);
            canvas.drawBitmap(frame, null, lcd, paint);
        } else {
            paint.setColor(Color.rgb(35, 36, 40));
            for (int x = 0; x < ViewportMapper.EMERY_WIDTH; x += 20) {
                float px = mapper.left + x * (mapper.right - mapper.left)
                        / ViewportMapper.EMERY_WIDTH;
                canvas.drawLine(px,mapper.top,px,mapper.bottom,paint);
            }
            for (int y = 0; y < ViewportMapper.EMERY_HEIGHT; y += 20) {
                float py = mapper.top + y * (mapper.bottom - mapper.top)
                        / ViewportMapper.EMERY_HEIGHT;
                canvas.drawLine(mapper.left,py,mapper.right,py,paint);
            }
            paint.setColor(ORANGE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.MONOSPACE);
            paint.setTextSize(Math.max(11, (mapper.right - mapper.left) / 13f));
            canvas.drawText("EMERY",lcd.centerX(),lcd.centerY()-18,paint);
            paint.setColor(Color.LTGRAY);
            paint.setTextSize(Math.max(8, (mapper.right - mapper.left) / 23f));
            canvas.drawText("RUNTIME NOT INSTALLED",lcd.centerX(),lcd.centerY()+14,paint);
            canvas.drawText("200 x 228",lcd.centerX(),lcd.centerY()+35,paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(ORANGE);
        canvas.drawRect(lcd,paint);
        paint.setStyle(Paint.Style.FILL);
        if (lastTouch != null && pointerDown) {
            float x = mapper.left + (lastTouch.x + .5f) *
                    (mapper.right-mapper.left) / mapper.sourceWidth;
            float y = mapper.top + (lastTouch.y + .5f) *
                    (mapper.bottom-mapper.top) / mapper.sourceHeight;
            paint.setColor(ORANGE);
            canvas.drawCircle(x,y,6,paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (mapper == null) return false;
        if (event.getPointerCount() != 1) {
            if (pointerDown) emit(InputType.CANCEL, lastTouch);
            pointerDown = false;
            lastTouch = null;
            invalidate();
            return true;
        }
        int action = event.getActionMasked();
        ViewportMapper.Point point = mapper.toPebble(event.getX(),event.getY());
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (point == null) return false;
                pointerDown = true; lastTouch = point;
                emit(InputType.DOWN,point); break;
            case MotionEvent.ACTION_MOVE:
                if (!pointerDown) return false;
                if (point == null) {
                    emit(InputType.CANCEL,lastTouch);
                    pointerDown = false; lastTouch = null;
                } else {
                    lastTouch = point; emit(InputType.MOVE,point);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!pointerDown) return false;
                emit(InputType.UP,point != null ? point : lastTouch);
                lastTouch = null; pointerDown = false; break;
            case MotionEvent.ACTION_CANCEL:
                if (!pointerDown) return false;
                emit(InputType.CANCEL,lastTouch);
                lastTouch = null; pointerDown = false; break;
            default: return false;
        }
        invalidate();
        return true;
    }

    private void emit(InputType type,ViewportMapper.Point point) {
        if (listener != null && point != null) listener.onInput(type,point.x,point.y);
    }

    public enum InputType { DOWN, MOVE, UP, CANCEL }
    public interface InputListener { void onInput(InputType type,int x,int y); }
}
