package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.os.Build;
import android.view.Display;

final class DisplayUtils {
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

    static boolean isSecondaryDisplay(Activity activity) {
        Display display = currentDisplay(activity);
        return display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
    }
}
