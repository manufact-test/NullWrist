package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.Collections;

final class RearUi {
    private RearUi() {}

    static void lockRearSurface(Activity activity, View surface) {
        Window window = activity.getWindow();
        try {
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            );
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
                surface.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) ->
                        applyGestureExclusion(view));
                surface.post(() -> applyGestureExclusion(surface));
            }
        } catch (RuntimeException ignored) {
            // Vendor secondary-display implementations can reject individual window flags.
        }
        enterImmersive(activity);
    }

    private static void applyGestureExclusion(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || view.getWidth() <= 0
                || view.getHeight() <= 0) {
            return;
        }
        try {
            view.setSystemGestureExclusionRects(Collections.singletonList(
                    new Rect(0, 0, view.getWidth(), view.getHeight())
            ));
        } catch (RuntimeException ignored) {
            // Gesture exclusion is best effort; mandatory system gestures remain controlled by Android.
        }
    }

    static void enterImmersive(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(
                            WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                    );
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (RuntimeException ignored) {
            // Some vendor display implementations reject one or more inset operations.
            // The rear surface must remain usable even when immersive mode is unavailable.
        }
    }

    static void leaveImmersive(Activity activity) {
        try {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(
                            WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                    );
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (RuntimeException ignored) {
            // Best-effort restoration for vendor display implementations.
        }
    }
}
