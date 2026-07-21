package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.runtime.PebbleRuntimeService;

import java.util.Map;
import java.util.WeakHashMap;

/** Adds polished runtime-mode controls to the existing reliability card. */
public final class RuntimeModeUi {
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_MIGRATION_ELIGIBLE =
            "runtime_mode_migration_eligible_0810";
    private static final String KEY_INTRO_SHOWN = "runtime_mode_intro_shown_0810";
    private static final String ORIGINAL_HINT_PREFIX = "For an always-on rear face:";
    private static final String RELIABLE_PREFIX = "RELIABLE ALWAYS-ON";
    private static final String SILENT_PREFIX = "SILENT MODE";
    private static final String BOUND_TAG = "pebblehertz-runtime-mode-bound";

    private static final Map<Activity, Boolean> INTRO_SCHEDULED = new WeakHashMap<>();
    private static final Map<Activity, Dialog> ACTIVE_DIALOGS = new WeakHashMap<>();

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

        boolean reliable = new AppPreferences(activity).isReliableRuntime();
        hint.setText(reliable
                ? "RELIABLE ALWAYS-ON · recommended for Titan 2. Tap to compare modes."
                : "SILENT MODE · Clear all or memory pressure may stop PebbleOS.");
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
            card.setOnClickListener(view -> showModeDialog(activity, false));
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
        showModeDialog(activity, true);
    }

    private static void showModeDialog(Activity activity, boolean introduction) {
        Dialog existing = ACTIVE_DIALOGS.get(activity);
        if (existing != null && existing.isShowing()) {
            return;
        }

        AppPreferences preferences = new AppPreferences(activity);
        boolean reliable = preferences.isReliableRuntime();

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(!introduction);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 12));
        root.setBackground(panel(
                activity.getColor(R.color.surface),
                activity.getColor(R.color.ink),
                dp(activity, 2),
                dp(activity, 3)
        ));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 13));
        header.setBackground(panel(
                activity.getColor(R.color.ink),
                activity.getColor(R.color.ink),
                0,
                dp(activity, 2)
        ));

        LinearLayout eyebrow = new LinearLayout(activity);
        eyebrow.setOrientation(LinearLayout.HORIZONTAL);
        eyebrow.setGravity(Gravity.CENTER_VERTICAL);

        TextView signal = pixelText(
                activity,
                "◆",
                14,
                activity.getColor(R.color.accent_mint)
        );
        signal.setPadding(0, 0, dp(activity, 8), 0);
        eyebrow.addView(signal);

        TextView kicker = pixelText(
                activity,
                "PEBBLEHERTZ 0.8.10 / RUNTIME",
                10,
                activity.getColor(R.color.accent_yellow)
        );
        kicker.setLetterSpacing(0.08f);
        eyebrow.addView(kicker);
        header.addView(eyebrow);

        TextView title = pixelText(
                activity,
                introduction ? "KEEP YOUR WATCHFACE ON AIR" : "RUNTIME RELIABILITY",
                introduction ? 20 : 19,
                activity.getColor(R.color.paper)
        );
        title.setPadding(0, dp(activity, 9), 0, dp(activity, 7));
        title.setLetterSpacing(0.025f);
        header.addView(title);

        TextView intro = bodyText(
                activity,
                introduction
                        ? "Titan 2 aggressively closes background apps. Choose how Pebblehertz "
                        + "protects PebbleOS when the main window is gone."
                        : "Reliable mode is designed for the rear display. Silent mode trades "
                        + "background protection for less Android system presence.",
                12,
                activity.getColor(R.color.paper)
        );
        intro.setAlpha(0.82f);
        header.addView(intro);
        root.addView(header, matchWidthWrapHeight(activity, 12));

        View reliableOption = modeOption(
                activity,
                true,
                reliable,
                "RECOMMENDED",
                "RELIABLE ALWAYS-ON",
                "Best for daily use. Pebblehertz keeps PebbleOS protected by Android and "
                        + "automatically recovers after Clear all when the system allows it.",
                "USE RELIABLE MODE  >"
        );
        reliableOption.setOnClickListener(view -> {
            applyMode(activity, AppPreferences.RuntimeMode.RELIABLE);
            dialog.dismiss();
        });
        root.addView(reliableOption, matchWidthWrapHeight(activity, 10));

        View silentOption = modeOption(
                activity,
                false,
                !reliable,
                "OPTIONAL",
                "SILENT MODE",
                "No foreground-service protection. Titan 2 may close PebbleOS after Clear all "
                        + "or when memory is needed.",
                "USE SILENT MODE"
        );
        silentOption.setOnClickListener(view -> {
            applyMode(activity, AppPreferences.RuntimeMode.SILENT);
            dialog.dismiss();
        });
        root.addView(silentOption, matchWidthWrapHeight(activity, 6));

        TextView systemNote = bodyText(
                activity,
                "Android still lists active foreground services in system controls. "
                        + "Pebblehertz does not add actions or alerts to its service notification.",
                10,
                activity.getColor(R.color.text_muted)
        );
        systemNote.setGravity(Gravity.CENTER);
        systemNote.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 8));
        root.addView(systemNote);

        TextView close = pixelText(
                activity,
                introduction ? "KEEP CURRENT MODE" : "CLOSE",
                10,
                activity.getColor(R.color.text_muted)
        );
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> dialog.dismiss());
        root.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            ACTIVE_DIALOGS.remove(activity);
            if (introduction) {
                activity.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_INTRO_SHOWN, true)
                        .apply();
            }
        });
        ACTIVE_DIALOGS.put(activity, dialog);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.getDecorView().setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        }
    }

    private static View modeOption(
            Activity activity,
            boolean recommended,
            boolean current,
            String badge,
            String title,
            String description,
            String action
    ) {
        int fill = recommended
                ? activity.getColor(R.color.accent_mint)
                : activity.getColor(R.color.paper);
        int pressed = blend(fill, activity.getColor(R.color.ink), recommended ? 0.13f : 0.08f);
        int stroke = recommended
                ? activity.getColor(R.color.ink)
                : activity.getColor(R.color.text_muted);
        int textColor = activity.getColor(R.color.ink);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(activity, recommended ? 15 : 13),
                dp(activity, recommended ? 14 : 12),
                dp(activity, recommended ? 15 : 13),
                dp(activity, recommended ? 14 : 12)
        );
        card.setMinimumHeight(dp(activity, recommended ? 132 : 110));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(interactivePanel(
                fill,
                pressed,
                stroke,
                dp(activity, recommended ? 2 : 1)
        ));
        if (recommended) {
            card.setElevation(dp(activity, 4));
        }

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView badgeView = pixelText(
                activity,
                badge,
                9,
                recommended ? activity.getColor(R.color.paper) : textColor
        );
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        badgeView.setBackground(panel(
                recommended ? activity.getColor(R.color.ink) : activity.getColor(R.color.surface_warm),
                activity.getColor(R.color.ink),
                dp(activity, 1),
                dp(activity, 2)
        ));
        top.addView(badgeView);

        if (current) {
            TextView selected = pixelText(
                    activity,
                    "CURRENT",
                    9,
                    recommended ? activity.getColor(R.color.ink) : activity.getColor(R.color.accent_coral)
            );
            selected.setGravity(Gravity.END);
            top.addView(selected, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
        } else {
            View spacer = new View(activity);
            top.addView(spacer, new LinearLayout.LayoutParams(
                    0,
                    1,
                    1f
            ));
        }
        card.addView(top);

        TextView heading = pixelText(activity, title, recommended ? 18 : 16, textColor);
        heading.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        heading.setLetterSpacing(0.02f);
        card.addView(heading);

        TextView copy = bodyText(activity, description, 12, textColor);
        copy.setAlpha(recommended ? 0.88f : 0.70f);
        card.addView(copy);

        TextView footer = pixelText(
                activity,
                action,
                10,
                recommended ? activity.getColor(R.color.ink) : activity.getColor(R.color.text_muted)
        );
        footer.setGravity(Gravity.END);
        footer.setPadding(0, dp(activity, 10), 0, 0);
        card.addView(footer);
        return card;
    }

    private static void applyMode(Activity activity, AppPreferences.RuntimeMode mode) {
        AppPreferences preferences = new AppPreferences(activity);
        preferences.setRuntimeMode(mode);
        PebbleRuntimeService.applyRuntimeMode(activity);
        bind(activity, activity.getWindow().getDecorView());

        Toast.makeText(
                activity,
                mode == AppPreferences.RuntimeMode.RELIABLE
                        ? "Reliable Always-On enabled"
                        : "Silent Mode enabled. Clear all may stop PebbleOS.",
                mode == AppPreferences.RuntimeMode.RELIABLE
                        ? Toast.LENGTH_SHORT
                        : Toast.LENGTH_LONG
        ).show();
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

    private static TextView pixelText(Activity activity, String value, int sizeSp, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0, 1f);
        return text;
    }

    private static TextView bodyText(Activity activity, String value, int sizeSp, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0, 1.1f);
        return text;
    }

    private static StateListDrawable interactivePanel(
            int normalColor,
            int pressedColor,
            int strokeColor,
            int strokeWidth
    ) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                panel(pressedColor, strokeColor, strokeWidth, 2)
        );
        states.addState(
                new int[]{android.R.attr.state_focused},
                panel(pressedColor, strokeColor, Math.max(strokeWidth, 2), 2)
        );
        states.addState(
                new int[]{},
                panel(normalColor, strokeColor, strokeWidth, 2)
        );
        return states;
    }

    private static GradientDrawable panel(
            int fillColor,
            int strokeColor,
            int strokeWidth,
            int cornerRadius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(cornerRadius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight(
            Activity activity,
            int bottomMargin
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(activity, bottomMargin);
        return params;
    }

    private static int blend(int first, int second, float ratio) {
        float inverse = 1f - ratio;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * ratio),
                Math.round(Color.green(first) * inverse + Color.green(second) * ratio),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * ratio)
        );
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
