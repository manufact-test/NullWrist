package com.manufacttest.pebblereardisplay.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.runtime.PebbleRuntimeService;

import java.util.Map;
import java.util.WeakHashMap;

/** Adds runtime-mode controls to the existing reliability card without coupling them to the locker. */
public final class RuntimeModeUi {
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_MIGRATION_ELIGIBLE =
            "runtime_mode_migration_eligible_0810";
    private static final String KEY_INTRO_SHOWN = "runtime_mode_intro_shown_0810";
    private static final String ORIGINAL_HINT_PREFIX = "For an always-on rear face:";
    private static final String RELIABLE_PREFIX = "RELIABLE ALWAYS-ON";
    private static final String SILENT_PREFIX = "SILENT MODE";
    private static final String BOUND_TAG = "pebblehertz-runtime-mode-bound";
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private static final Map<Activity, Boolean> INTRO_SCHEDULED = new WeakHashMap<>();

    private RuntimeModeUi() {
    }

    public static void initializeMigration(Context context) {
        SharedPreferences setup = context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE);
        if (setup.contains(KEY_MIGRATION_ELIGIBLE)) {
            return;
        }
        setup.edit().putBoolean(
                KEY_MIGRATION_ELIGIBLE,
                new AppPreferences(context).hasSavedWatchfaceSelection()
        ).apply();
    }

    public static void bind(Activity activity, View root) {
        TextView hint = findRuntimeHint(root);
        if (hint == null) {
            return;
        }
        AppPreferences preferences = new AppPreferences(activity);
        boolean reliable = preferences.isReliableRuntime();
        hint.setText(reliable
                ? "RELIABLE ALWAYS-ON · foreground service active. Tap to change mode."
                : "SILENT MODE · Android may stop PebbleOS after Clear all. Tap to change mode.");
        hint.setTextColor(activity.getColor(
                reliable ? R.color.accent_mint : R.color.accent_yellow
        ));

        if (!(hint.getParent() instanceof View)) {
            return;
        }
        View card = (View) hint.getParent();
        if (!BOUND_TAG.equals(card.getTag())) {
            card.setTag(BOUND_TAG);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(view -> showModeDialog(activity));
        }

        if (card instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) card;
            if (group.getChildCount() > 0 && group.getChildAt(0) instanceof TextView) {
                TextView icon = (TextView) group.getChildAt(0);
                icon.setText(reliable ? "R" : "S");
                icon.setTextColor(activity.getColor(
                        reliable ? R.color.accent_mint : R.color.accent_yellow
                ));
            }
        }
    }

    public static void scheduleMigrationIntro(Activity activity, View decor) {
        SharedPreferences setup = activity.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE);
        if (!setup.getBoolean(KEY_MIGRATION_ELIGIBLE, false)
                || setup.getBoolean(KEY_INTRO_SHOWN, false)
                || INTRO_SCHEDULED.containsKey(activity)) {
            return;
        }
        INTRO_SCHEDULED.put(activity, true);
        setup.edit().putBoolean(KEY_INTRO_SHOWN, true).apply();
        decor.postDelayed(() -> showIntroWhenReady(activity, decor, 0), 650L);
    }

    private static void showIntroWhenReady(Activity activity, View decor, int attempt) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (!activity.hasWindowFocus() && attempt < 12) {
            decor.postDelayed(() -> showIntroWhenReady(activity, decor, attempt + 1), 500L);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Reliable Always-On is now the default")
                .setMessage("Pebblehertz now keeps PebbleOS in an Android foreground service, "
                        + "so Clear all is less likely to stop the rear watchface. Android may "
                        + "show a small silent ongoing notification. You can switch to Silent "
                        + "Mode by tapping the Runtime Reliability card.")
                .setPositiveButton("Use Reliable", (dialog, which) ->
                        applyMode(activity, AppPreferences.RuntimeMode.RELIABLE))
                .setNeutralButton("Use Silent", (dialog, which) ->
                        applyMode(activity, AppPreferences.RuntimeMode.SILENT))
                .setNegativeButton("Keep current", null)
                .show();
    }

    private static void showModeDialog(Activity activity) {
        AppPreferences preferences = new AppPreferences(activity);
        boolean reliable = preferences.isReliableRuntime();
        String current = reliable
                ? "Current mode: Reliable Always-On. PebbleOS uses a silent foreground service."
                : "Current mode: Silent. Android may stop PebbleOS after Clear all or under memory pressure.";
        new AlertDialog.Builder(activity)
                .setTitle("Runtime Reliability")
                .setMessage(current + "\n\nReliable is recommended for the Titan 2 rear display.")
                .setPositiveButton("Reliable", (dialog, which) ->
                        applyMode(activity, AppPreferences.RuntimeMode.RELIABLE))
                .setNeutralButton("Silent", (dialog, which) ->
                        applyMode(activity, AppPreferences.RuntimeMode.SILENT))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void applyMode(Activity activity, AppPreferences.RuntimeMode mode) {
        AppPreferences preferences = new AppPreferences(activity);
        preferences.setRuntimeMode(mode);
        PebbleRuntimeService.applyRuntimeMode(activity);
        bind(activity, activity.getWindow().getDecorView());

        if (mode == AppPreferences.RuntimeMode.RELIABLE) {
            requestNotificationPermissionIfNeeded(activity);
            Toast.makeText(
                    activity,
                    "Reliable Always-On enabled",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    activity,
                    "Silent Mode enabled. Android may stop the rear watchface.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private static void requestNotificationPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS
        );
    }

    private static TextView findRuntimeHint(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence value = textView.getText();
            if (value != null) {
                String text = value.toString();
                if (text.startsWith(ORIGINAL_HINT_PREFIX)
                        || text.startsWith(RELIABLE_PREFIX)
                        || text.startsWith(SILENT_PREFIX)) {
                    return textView;
                }
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView found = findRuntimeHint(group.getChildAt(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
