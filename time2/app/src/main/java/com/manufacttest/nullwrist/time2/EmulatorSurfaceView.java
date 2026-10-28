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
    private boolean diagnosticOverlay;
    private String emptyLabel="PEBBLEOS STARTING";

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

    public void setDiagnosticOverlay(boolean enabled) {
        this.diagnosticOverlay=enabled;
        invalidate();
    }

    public void setEmptyLabel(String label) {
        this.emptyLabel=label==null?"":label;
        invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        mapper = w > 0 && h > 0
                ? ViewportMapper.fit(ViewportMapper.EMERY_WIDTH, ViewportMapper.EMERY_HEIGHT, w, h)
                : null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mapper==null)return;
        RectF lcd=new RectF(mapper.left,mapper.top,mapper.right,mapper.bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        if(frame!=null) {
            // This is the genuine PebbleOS 200x228 ARGB framebuffer.
            // No Android overlays, borders, gradients or bilinear smoothing.
            canvas.drawBitmap(frame,null,lcd,paint);
        } else {
            paint.setColor(Color.rgb(235,236,232));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.MONOSPACE);
            paint.setTextSize(Math.min(17f,Math.max(11f,getWidth()/23f)));
            canvas.drawText(emptyLabel,lcd.centerX(),
                    lcd.centerY(),paint);
        }
        if(diagnosticOverlay) {
            paint.setColor(ORANGE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawRect(lcd,paint);
            paint.setStyle(Paint.Style.FILL);
            if(lastTouch!=null && pointerDown) {
                float x=mapper.left+(lastTouch.x+.5f)*
                        (mapper.right-mapper.left)/mapper.sourceWidth;
                float y=mapper.top+(lastTouch.y+.5f)*
                        (mapper.bottom-mapper.top)/mapper.sourceHeight;
                canvas.drawCircle(x,y,5f,paint);
            }
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
