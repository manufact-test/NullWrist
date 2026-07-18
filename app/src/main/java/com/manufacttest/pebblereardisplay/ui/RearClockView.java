package com.manufacttest.pebblereardisplay.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RearClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WatchfaceRepository repository;
    private final AppPreferences preferences;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());

    RearClockView(
            Context context,
            WatchfaceRepository repository,
            AppPreferences preferences
    ) {
        super(context);
        this.repository = repository;
        this.preferences = preferences;
        setBackgroundColor(Color.BLACK);
        setFocusable(false);
        setClickable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float padding = Math.min(width, height) * 0.07f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, Math.min(width, height) * 0.008f));
        paint.setColor(Color.rgb(101, 213, 199));
        canvas.drawRoundRect(
                new RectF(padding, padding, width - padding, height - padding),
                padding * 0.45f,
                padding * 0.45f,
                paint
        );

        Date now = new Date();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
        ));
        paint.setTextSize(Math.min(width, height) * 0.25f);
        canvas.drawText(timeFormat.format(now), width / 2f, height * 0.53f, paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.min(width, height) * 0.065f);
        paint.setColor(Color.LTGRAY);
        canvas.drawText(dateFormat.format(now), width / 2f, height * 0.66f, paint);

        WatchfaceMetadata selected = selectedWatchface();
        paint.setTextSize(Math.min(width, height) * 0.047f);
        paint.setColor(Color.rgb(101, 213, 199));
        canvas.drawText(
                selected == null ? "No watchface selected" : selected.getName(),
                width / 2f,
                height * 0.82f,
                paint
        );

        paint.setTextSize(Math.min(width, height) * 0.032f);
        paint.setColor(Color.GRAY);
        canvas.drawText(
                "Pebble runtime integration in progress",
                width / 2f,
                height * 0.90f,
                paint
        );

        long delay = 60_000L - (System.currentTimeMillis() % 60_000L) + 20L;
        postInvalidateDelayed(delay);
    }

    private WatchfaceMetadata selectedWatchface() {
        try {
            return repository.findByStorageId(preferences.getSelectedWatchfaceId());
        } catch (IOException ignored) {
            return null;
        }
    }
}
