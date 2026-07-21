package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

/** Draws real 144x168 frames without smoothing and a deterministic pixel fallback. */
public final class PixelWatchfaceThumbnailView extends View {
    private static final int[] FALLBACK_COLORS = {
            Color.rgb(79, 209, 174),
            Color.rgb(255, 91, 77),
            Color.rgb(255, 211, 78),
            Color.rgb(91, 145, 255),
            Color.rgb(180, 126, 255)
    };

    private final Paint paint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source = new Rect();
    private final RectF destination = new RectF();

    private Bitmap bitmap;
    private WatchfaceMetadata metadata;
    private boolean active;

    public PixelWatchfaceThumbnailView(Context context) {
        super(context);
        paint.setFilterBitmap(false);
        paint.setDither(false);
        textPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setWatchface(WatchfaceMetadata metadata, Bitmap bitmap, boolean active) {
        this.metadata = metadata;
        this.bitmap = bitmap;
        this.active = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int border = Math.max(2, Math.round(getResources().getDisplayMetrics().density * 2));
        int shadow = Math.max(3, Math.round(getResources().getDisplayMetrics().density * 4));
        int ink = Color.rgb(23, 32, 42);
        int paper = Color.rgb(255, 253, 245);
        int mint = Color.rgb(79, 209, 174);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ink);
        canvas.drawRect(shadow, shadow, width, height, paint);

        paint.setColor(active ? mint : paper);
        canvas.drawRect(0, 0, width - shadow, height - shadow, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(border);
        paint.setColor(ink);
        canvas.drawRect(
                border / 2f,
                border / 2f,
                width - shadow - border / 2f,
                height - shadow - border / 2f,
                paint
        );

        float inset = border * 2.5f;
        destination.set(
                inset,
                inset,
                width - shadow - inset,
                height - shadow - inset
        );

        if (bitmap != null && !bitmap.isRecycled()) {
            source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setFilterBitmap(false);
            canvas.drawBitmap(bitmap, source, destination, paint);
        } else {
            drawFallback(canvas, destination, ink, paper);
        }

        if (active) {
            float dot = Math.max(4f, border * 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 91, 77));
            canvas.drawRect(
                    width - shadow - inset - dot,
                    inset,
                    width - shadow - inset,
                    inset + dot,
                    paint
            );
        }
    }

    private void drawFallback(Canvas canvas, RectF area, int ink, int paper) {
        int seed = metadata == null ? 0 : metadata.getStorageId().hashCode();
        int primary = FALLBACK_COLORS[Math.floorMod(seed, FALLBACK_COLORS.length)];
        int secondary = FALLBACK_COLORS[Math.floorMod(seed / 7 + 2, FALLBACK_COLORS.length)];

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ink);
        canvas.drawRect(area, paint);

        int columns = 8;
        int rows = 10;
        float cellWidth = area.width() / columns;
        float cellHeight = area.height() / rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int value = seed ^ (row * 31) ^ (column * 17);
                if ((value & 3) == 0 || (row + column) % 7 == 0) {
                    paint.setColor(((value >>> 3) & 1) == 0 ? primary : secondary);
                    canvas.drawRect(
                            area.left + column * cellWidth,
                            area.top + row * cellHeight,
                            area.left + (column + 1) * cellWidth,
                            area.top + (row + 1) * cellHeight,
                            paint
                    );
                }
            }
        }

        String initials = initials(metadata == null ? "PB" : metadata.getName());
        textPaint.setColor(paper);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.min(area.width(), area.height()) * 0.27f);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = area.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(initials, area.centerX(), baseline, textPaint);
    }

    private static String initials(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "PB";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
    }
}
