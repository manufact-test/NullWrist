package com.manufacttest.pebblereardisplay;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.WeakHashMap;

/** Installs the project-support dialog without coupling it to the watchface UI code. */
public final class PebblehertzApplication extends Application
        implements Application.ActivityLifecycleCallbacks {

    private static final String MAIN_ACTIVITY =
            "com.manufacttest.pebblereardisplay.ui.MainActivity";
    private static final String SUPPORT_LABEL = "SUPPORT PEBBLEHERTZ";
    private static final String SUPPORT_BOUND_TAG = "pebblehertz-support-dialog-bound";

    private final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> layoutListeners =
            new WeakHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!MAIN_ACTIVITY.equals(activity.getClass().getName())) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        bindSupportButton(activity, decor);
        RuntimeModeUi.bind(activity, decor);
        RuntimeModeUi.scheduleMigrationIntro(activity, decor);
        if (layoutListeners.containsKey(activity)) {
            return;
        }
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            bindSupportButton(activity, decor);
            RuntimeModeUi.bind(activity, decor);
        };
        layoutListeners.put(activity, listener);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener = layoutListeners.remove(activity);
        if (listener == null) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(listener);
        }
    }

    private static void bindSupportButton(Activity activity, View root) {
        TextView button = findSupportButton(root);
        if (button == null || SUPPORT_BOUND_TAG.equals(button.getTag())) {
            return;
        }
        button.setTag(SUPPORT_BOUND_TAG);
        button.setOnClickListener(view -> showSupportDialog(activity));
    }

    private static TextView findSupportButton(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (SUPPORT_LABEL.contentEquals(textView.getText())) {
                return textView;
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView found = findSupportButton(group.getChildAt(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void showSupportDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 13));
        root.setBackground(panel(
                activity.getColor(R.color.paper),
                activity.getColor(R.color.ink),
                dp(activity, 2),
                dp(activity, 3)
        ));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView signal = pixelText(
                activity,
                "◆",
                17,
                activity.getColor(R.color.accent_coral)
        );
        signal.setPadding(0, 0, dp(activity, 9), 0);
        heading.addView(signal);

        TextView title = pixelText(
                activity,
                "SUPPORT PEBBLEHERTZ",
                18,
                activity.getColor(R.color.ink)
        );
        title.setLetterSpacing(0.025f);
        heading.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        root.addView(heading);

        TextView subtitle = bodyText(
                activity,
                "Pebblehertz stays free. Support is completely optional, "
                        + "but every contribution helps the project grow.",
                13,
                activity.getColor(R.color.text_secondary)
        );
        subtitle.setPadding(0, dp(activity, 9), 0, dp(activity, 16));
        root.addView(subtitle);

        View wise = platformButton(
                activity,
                "W",
                "WISE",
                "FAST INTERNATIONAL SUPPORT",
                Color.parseColor("#9FE870"),
                Color.parseColor("#163300"),
                Color.parseColor("#163300")
        );
        wise.setOnClickListener(view -> {
            dialog.dismiss();
            openExternalLink(activity, "https://wise.com/pay/me/ilyas709");
        });
        root.addView(wise, matchWidthWrapHeight(activity, 10));

        View paypal = platformButton(
                activity,
                "P",
                "PAYPAL",
                "CARD OR PAYPAL BALANCE",
                Color.parseColor("#0070E0"),
                Color.parseColor("#003087"),
                Color.WHITE
        );
        paypal.setOnClickListener(view -> {
            dialog.dismiss();
            openExternalLink(activity, "https://www.paypal.me/myarrogantfox");
        });
        root.addView(paypal, matchWidthWrapHeight(activity, 9));

        TextView close = pixelText(
                activity,
                "CLOSE",
                10,
                activity.getColor(R.color.text_muted)
        );
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 6));
        close.setAlpha(0.62f);
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> dialog.dismiss());

        LinearLayout closeRow = new LinearLayout(activity);
        closeRow.setGravity(Gravity.END);
        closeRow.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(closeRow);

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.getDecorView().setPadding(dp(activity, 18), 0, dp(activity, 18), 0);
        }
    }

    private static View platformButton(
            Activity activity,
            String mark,
            String platform,
            String note,
            int fillColor,
            int markColor,
            int textColor
    ) {
        LinearLayout button = new LinearLayout(activity);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(
                dp(activity, 10),
                dp(activity, 9),
                dp(activity, 12),
                dp(activity, 9)
        );
        button.setMinimumHeight(dp(activity, 66));
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(interactivePanel(
                activity,
                fillColor,
                blend(fillColor, activity.getColor(R.color.ink), 0.14f),
                activity.getColor(R.color.ink)
        ));

        TextView badge = pixelText(activity, mark, 22, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(panel(
                markColor,
                activity.getColor(R.color.ink),
                dp(activity, 1),
                dp(activity, 2)
        ));
        button.addView(badge, new LinearLayout.LayoutParams(
                dp(activity, 46),
                dp(activity, 46)
        ));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);

        TextView name = pixelText(activity, platform, 17, textColor);
        name.setLetterSpacing(0.035f);
        labels.addView(name);

        TextView description = pixelText(activity, note, 9, textColor);
        description.setAlpha(0.76f);
        description.setPadding(0, dp(activity, 4), 0, 0);
        labels.addView(description);

        button.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView arrow = pixelText(activity, ">", 20, textColor);
        arrow.setGravity(Gravity.CENTER);
        button.addView(arrow, new LinearLayout.LayoutParams(
                dp(activity, 24),
                dp(activity, 46)
        ));
        return button;
    }

    private static TextView pixelText(
            Activity activity,
            String value,
            int sizeSp,
            int color
    ) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0, 1f);
        return text;
    }

    private static TextView bodyText(
            Activity activity,
            String value,
            int sizeSp,
            int color
    ) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        text.setLineSpacing(0, 1.08f);
        return text;
    }

    private static StateListDrawable interactivePanel(
            Activity activity,
            int normalColor,
            int pressedColor,
            int strokeColor
    ) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                panel(pressedColor, strokeColor, dp(activity, 2), dp(activity, 2))
        );
        states.addState(
                new int[]{android.R.attr.state_focused},
                panel(
                        pressedColor,
                        activity.getColor(R.color.accent_coral),
                        dp(activity, 3),
                        dp(activity, 2)
                )
        );
        states.addState(
                new int[]{},
                panel(normalColor, strokeColor, dp(activity, 2), dp(activity, 2))
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

    private static void openExternalLink(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException exception) {
            Toast.makeText(
                    activity,
                    "Cannot open the donation page",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
