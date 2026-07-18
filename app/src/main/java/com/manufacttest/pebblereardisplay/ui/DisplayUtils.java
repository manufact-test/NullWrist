package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;
import android.view.WindowMetrics;

final class DisplayUtils {
    static final String EXTRA_FORCE_REAR_MODE =
            "com.manufacttest.pebblereardisplay.extra.FORCE_REAR_MODE";
    static final String EXTRA_PREVIEW_MODE =
            "com.manufacttest.pebblereardisplay.extra.PREVIEW_MODE";

    // Titan 2 firmware can expose the rear app window as display 0. In that case
    // the compact window bounds are a more reliable signal than displayId.
    private static final int MAX_REAR_SHORT_EDGE_PX = 720;
    private static final int MAX_REAR_LONG_EDGE_PX = 900;

    private DisplayUtils() {}

    static Display currentDisplay(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = activity.getDisplay();
            if (display != null) {
                return display;
            }
        }
        return activity.getWindowManager().getDefaultDisplay();
    }

    static boolean shouldUseRearMode(Activity activity, Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_FORCE_REAR_MODE, false)) {
            return true;
        }

        Display display = currentDisplay(activity);
        if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return true;
        }

        Point windowSize = currentWindowSize(activity);
        return isCompactRearBounds(windowSize.x, windowSize.y);
    }

    static boolean isCompactRearBounds(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) {
            return false;
        }
        int shortEdge = Math.min(widthPx, heightPx);
        int longEdge = Math.max(widthPx, heightPx);
        return shortEdge <= MAX_REAR_SHORT_EDGE_PX && longEdge <= MAX_REAR_LONG_EDGE_PX;
    }

    static Point currentWindowSize(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            return new Point(bounds.width(), bounds.height());
        }

        Point result = new Point();
        Display display = currentDisplay(activity);
        if (display != null) {
            display.getRealSize(result);
        }
        return result;
    }
}