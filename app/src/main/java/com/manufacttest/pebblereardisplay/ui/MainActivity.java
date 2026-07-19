package com.manufacttest.pebblereardisplay.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.data.WatchfaceThumbnailRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;
import com.manufacttest.pebblereardisplay.runtime.PebbleQemuProcess;
import com.manufacttest.pebblereardisplay.runtime.PebbleRuntimeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_PBW = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown";

    private WatchfaceRepository repository;
    private WatchfaceThumbnailRepository thumbnails;
    private AppPreferences preferences;
    private LinearLayout catalogContainer;
    private PixelWatchfaceThumbnailView heroPreview;
    private TextView heroName;
    private TextView heroMeta;
    private TextView runtimeStatusLabel;
    private TextView runtimeLed;
    private List<WatchfaceMetadata> watchfaces = new ArrayList<>();
    private boolean rearMode;
    private boolean redirectingToRear;
    private boolean listenersRegistered;
    private String renderedActiveStorageId;

    private final PebbleRuntimeService.Listener runtimeListener = (
            PebbleQemuProcess runtime,
            String status,
            String failure
    ) -> runOnUiThread(() -> {
        updateRuntimeStatus(status, failure);
        String activeId = PebbleRuntimeService.getActiveStorageId();
        if (!sameStorageId(renderedActiveStorageId, activeId)) {
            renderCatalog();
        }
    });

    private final BroadcastReceiver thumbnailReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!rearMode) {
                renderCatalog();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderForCurrentSurface();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!rearMode) {
            registerMainListeners();
        }
    }

    @Override
    protected void onStop() {
        unregisterMainListeners();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderForCurrentSurface();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        renderForCurrentSurface();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            return;
        }
        if (rearMode) {
            RearUi.enterImmersive(this);
        } else {
            scheduleRearModeRecheck();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return rearMode || super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (!rearMode) {
            super.onBackPressed();
        }
    }

    private void renderForCurrentSurface() {
        if (DisplayUtils.shouldUseRearMode(this, getIntent())) {
            showRearSurface();
        } else {
            showMainSurface();
        }
    }

    private void showRearSurface() {
        unregisterMainListeners();
        if (!redirectingToRear) {
            redirectingToRear = true;
            Intent rear = new Intent(this, RearDisplayActivity.class)
                    .putExtra(DisplayUtils.EXTRA_FORCE_REAR_MODE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                if (getDisplay() != null) {
                    options.setLaunchDisplayId(getDisplay().getDisplayId());
                }
                startActivity(rear, options.toBundle());
                finish();
                return;
            } catch (RuntimeException ignored) {
                redirectingToRear = false;
            }
        }

        rearMode = true;
        setContentView(new PebbleOsSurfaceView(this));
        getWindow().getDecorView().post(() -> RearUi.enterImmersive(this));
    }

    private void showMainSurface() {
        rearMode = false;
        redirectingToRear = false;
        RearUi.leaveImmersive(this);
        repository = new WatchfaceRepository(this);
        thumbnails = new WatchfaceThumbnailRepository(this);
        preferences = new AppPreferences(this);
        setContentView(buildMainScreen());
        reloadCatalog();
        registerMainListeners();
        PebbleRuntimeService.start(this);
        maybeRequestBackgroundSetup();
        scheduleRearModeRecheck();
    }

    private void registerMainListeners() {
        if (listenersRegistered || rearMode) {
            return;
        }
        PebbleRuntimeService.addListener(runtimeListener);
        IntentFilter filter = new IntentFilter(WatchfaceThumbnailRepository.ACTION_THUMBNAIL_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(thumbnailReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(thumbnailReceiver, filter);
        }
        listenersRegistered = true;
    }

    private void unregisterMainListeners() {
        if (!listenersRegistered) {
            return;
        }
        PebbleRuntimeService.removeListener(runtimeListener);
        try {
            unregisterReceiver(thumbnailReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        listenersRegistered = false;
    }

    private void scheduleRearModeRecheck() {
        View decor = getWindow().getDecorView();
        decor.post(() -> {
            if (!rearMode && DisplayUtils.isCompactRearBounds(decor.getWidth(), decor.getHeight())) {
                showRearSurface();
            }
        });
    }

    private View buildMainScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildHeader(), matchWidthWrapHeight(dp(14)));
        root.addView(buildHeroCard(), matchWidthWrapHeight(dp(12)));
        root.addView(buildActionRow(), matchWidthWrapHeight(dp(10)));
        root.addView(buildReliabilityCard(), matchWidthWrapHeight(dp(20)));

        TextView listTitle = pixelText("WATCHFACE LOCKER // 00", 16, getColor(R.color.text_primary));
        listTitle.setTag("locker-title");
        listTitle.setPadding(dp(2), 0, 0, dp(10));
        root.addView(listTitle);

        catalogContainer = new LinearLayout(this);
        catalogContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(catalogContainer, matchWidthWrapHeight(0));

        TextView footer = pixelText("PEBBLE TIME / BASALT 144x168", 11, getColor(R.color.text_muted));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer);
        return scroll;
    }

    private View buildHeader() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(15));
        panel.setBackground(panelBackground(
                getColor(R.color.ink),
                getColor(R.color.ink),
                0
        ));
        panel.setElevation(dp(3));

        TextView title = pixelText("PEBBLEHERTZ", 30, getColor(R.color.paper));
        title.setLetterSpacing(0.04f);
        panel.addView(title);

        TextView subtitle = pixelText(
                "WATCHFACE SIGNAL FOR TITAN 2",
                11,
                getColor(R.color.accent_yellow)
        );
        subtitle.setLetterSpacing(0.08f);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        panel.addView(subtitle);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);

        runtimeLed = pixelText("■", 15, getColor(R.color.accent_mint));
        status.addView(runtimeLed);

        runtimeStatusLabel = pixelText("RUNTIME STARTING", 12, getColor(R.color.paper));
        runtimeStatusLabel.setPadding(dp(8), 0, 0, 0);
        status.addView(runtimeStatusLabel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        panel.addView(status);
        return panel;
    }

    private View buildHeroCard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(panelBackground(
                getColor(R.color.surface),
                getColor(R.color.ink),
                dp(2)
        ));
        panel.setElevation(dp(3));

        heroPreview = new PixelWatchfaceThumbnailView(this);
        panel.addView(heroPreview, new LinearLayout.LayoutParams(dp(112), dp(132)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(16), 0, 0, 0);
        panel.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView label = pixelText("NOW TRANSMITTING", 11, getColor(R.color.accent_coral));
        label.setLetterSpacing(0.06f);
        copy.addView(label);

        heroName = pixelText("NO FACE", 22, getColor(R.color.text_primary));
        heroName.setPadding(0, dp(7), 0, dp(5));
        copy.addView(heroName);

        heroMeta = bodyText("Select a watchface from the locker.", 13, getColor(R.color.text_secondary));
        heroMeta.setPadding(0, 0, 0, dp(12));
        copy.addView(heroMeta);

        TextView previewButton = pixelButton(
                "OPEN REAR PREVIEW",
                getColor(R.color.accent_mint),
                getColor(R.color.ink)
        );
        previewButton.setOnClickListener(view -> openRearPreview());
        copy.addView(previewButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        return panel;
    }

    private View buildActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView importButton = pixelButton(
                "+ IMPORT PBW",
                getColor(R.color.accent_yellow),
                getColor(R.color.ink)
        );
        importButton.setOnClickListener(view -> openPbwPicker());
        row.addView(importButton, weightedButtonParams(dp(6)));

        TextView settingsButton = pixelButton(
                "SYSTEM SETUP",
                getColor(R.color.paper),
                getColor(R.color.ink)
        );
        settingsButton.setOnClickListener(view -> openBackgroundSettings());
        row.addView(settingsButton, weightedButtonParams(0));
        return row;
    }

    private View buildReliabilityCard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(13), dp(11), dp(13), dp(11));
        panel.setBackground(panelBackground(
                getColor(R.color.surface_warm),
                getColor(R.color.ink),
                dp(1)
        ));

        TextView icon = pixelText("!", 20, getColor(R.color.accent_coral));
        icon.setGravity(Gravity.CENTER);
        panel.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(38)));

        TextView hint = bodyText(
                "For an always-on rear face: allow DuraSpeed, disable App blocker and use Unrestricted battery mode.",
                12,
                getColor(R.color.text_secondary)
        );
        hint.setPadding(dp(7), 0, 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return panel;
    }

    private void openRearPreview() {
        Intent preview = new Intent(this, RearDisplayActivity.class);
        preview.putExtra(DisplayUtils.EXTRA_FORCE_REAR_MODE, true);
        preview.putExtra(DisplayUtils.EXTRA_PREVIEW_MODE, true);
        try {
            startActivity(preview);
        } catch (RuntimeException exception) {
            showError("Cannot open rear preview: " + exception.getClass().getSimpleName());
        }
    }

    private void reloadCatalog() {
        try {
            watchfaces = repository.loadAll();
            ensureSelection();
            renderCatalog();
        } catch (IOException exception) {
            showError("Cannot load watchfaces: " + exception.getMessage());
        }
    }

    private void ensureSelection() {
        String selectedId = preferences.getSelectedWatchfaceId();
        if (selectedId == null && !watchfaces.isEmpty()) {
            preferences.setSelectedWatchfaceId(watchfaces.get(0).getStorageId());
        }
    }

    private void renderCatalog() {
        if (catalogContainer == null || preferences == null || thumbnails == null) {
            return;
        }
        catalogContainer.removeAllViews();
        String selectedId = preferences.getSelectedWatchfaceId();
        String activeId = PebbleRuntimeService.getActiveStorageId();
        renderedActiveStorageId = activeId;
        WatchfaceMetadata selected = null;

        View root = catalogContainer.getParent() instanceof View
                ? (View) catalogContainer.getParent()
                : null;
        if (root instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) root;
            for (int index = 0; index < parent.getChildCount(); index++) {
                View child = parent.getChildAt(index);
                if ("locker-title".equals(child.getTag()) && child instanceof TextView) {
                    ((TextView) child).setText(String.format(
                            Locale.US,
                            "WATCHFACE LOCKER // %02d",
                            watchfaces.size()
                    ));
                }
            }
        }

        for (WatchfaceMetadata watchface : watchfaces) {
            boolean selectedInUi = watchface.getStorageId().equals(selectedId);
            boolean active = watchface.getStorageId().equals(activeId);
            if (selectedInUi) {
                selected = watchface;
            }
            catalogContainer.addView(
                    watchfaceCard(watchface, selectedInUi, active),
                    matchWidthWrapHeight(dp(11))
            );
        }

        if (watchfaces.isEmpty()) {
            TextView empty = bodyText(
                    "No watchfaces found. Import a .pbw file to begin.",
                    15,
                    getColor(R.color.text_secondary)
            );
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            empty.setBackground(panelBackground(
                    getColor(R.color.surface),
                    getColor(R.color.ink),
                    dp(2)
            ));
            catalogContainer.addView(empty);
        }
        updateHero(selected);
    }

    private View watchfaceCard(
            WatchfaceMetadata watchface,
            boolean selected,
            boolean active
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setMinimumHeight(dp(116));
        card.setBackground(interactivePanelBackground(
                selected ? getColor(R.color.surface_selected) : getColor(R.color.surface),
                getColor(R.color.surface_pressed),
                selected ? getColor(R.color.accent_coral) : getColor(R.color.ink)
        ));
        card.setElevation(dp(3));
        card.setClickable(true);
        card.setFocusable(true);

        Bitmap bitmap = thumbnails.load(watchface);
        PixelWatchfaceThumbnailView preview = new PixelWatchfaceThumbnailView(this);
        preview.setWatchface(watchface, bitmap, selected);
        card.addView(preview, new LinearLayout.LayoutParams(dp(82), dp(98)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        card.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView name = pixelText(
                watchface.getName().toUpperCase(Locale.ROOT),
                17,
                getColor(R.color.text_primary)
        );
        name.setMaxLines(2);
        copy.addView(name);

        TextView meta = bodyText(
                watchface.getAuthor() + "  /  v" + watchface.getVersion(),
                12,
                getColor(R.color.text_secondary)
        );
        meta.setMaxLines(1);
        meta.setPadding(0, dp(4), 0, dp(8));
        copy.addView(meta);

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        badges.addView(badge(
                watchface.isBundled() ? "BUNDLED" : "IMPORTED",
                getColor(R.color.ink),
                getColor(R.color.paper)
        ));
        if (watchface.hasPhoneJavaScript()) {
            TextView js = badge("PHONE JS", getColor(R.color.accent_yellow), getColor(R.color.ink));
            LinearLayout.LayoutParams jsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(26)
            );
            jsParams.leftMargin = dp(6);
            badges.addView(js, jsParams);
        }
        if (selected && !active) {
            TextView queuedBadge = badge("QUEUED", getColor(R.color.accent_yellow), getColor(R.color.ink));
            LinearLayout.LayoutParams queuedParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(26)
            );
            queuedParams.leftMargin = dp(6);
            badges.addView(queuedBadge, queuedParams);
        }
        if (active) {
            TextView activeBadge = badge("ACTIVE", getColor(R.color.accent_coral), Color.WHITE);
            LinearLayout.LayoutParams activeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(26)
            );
            activeParams.leftMargin = dp(6);
            badges.addView(activeBadge, activeParams);
        }
        copy.addView(badges);

        TextView action = pixelText(
                active ? "ON AIR" : selected ? "APPLYING..." : "TAP TO APPLY >",
                11,
                active
                        ? getColor(R.color.accent_coral)
                        : selected
                        ? getColor(R.color.accent_yellow)
                        : getColor(R.color.text_muted)
        );
        action.setPadding(0, dp(8), 0, 0);
        copy.addView(action);

        card.setOnClickListener(view -> applyWatchface(watchface));
        return card;
    }

    private void updateHero(WatchfaceMetadata selected) {
        if (heroPreview == null || heroName == null || heroMeta == null) {
            return;
        }
        if (selected == null) {
            heroPreview.setWatchface(null, null, false);
            heroName.setText("NO FACE");
            heroMeta.setText("Select a watchface from the locker.");
            return;
        }
        heroPreview.setWatchface(selected, thumbnails.load(selected), true);
        heroName.setText(selected.getName().toUpperCase(Locale.ROOT));
        heroMeta.setText(
                selected.getAuthor()
                        + "\nPebble Time / "
                        + (selected.isBundled() ? "bundled" : "imported")
        );
    }

    private void applyWatchface(WatchfaceMetadata watchface) {
        preferences.setSelectedWatchfaceId(watchface.getStorageId());
        renderCatalog();
        updateRuntimeStatus("Launching " + watchface.getName(), null);
        PebbleRuntimeService.select(this);
    }

    private void updateRuntimeStatus(String status, String failure) {
        if (runtimeStatusLabel == null || runtimeLed == null) {
            return;
        }
        if (failure != null) {
            runtimeLed.setTextColor(getColor(R.color.error));
            runtimeStatusLabel.setText("RUNTIME ERROR");
            runtimeStatusLabel.setTextColor(getColor(R.color.error));
            return;
        }
        runtimeStatusLabel.setTextColor(getColor(R.color.paper));
        if (status != null && !status.isBlank()) {
            runtimeLed.setTextColor(getColor(R.color.accent_yellow));
            runtimeStatusLabel.setText(shortStatus(status));
        } else {
            runtimeLed.setTextColor(getColor(R.color.accent_mint));
            runtimeStatusLabel.setText("RUNTIME ONLINE");
        }
    }

    private static boolean sameStorageId(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String shortStatus(String value) {
        String upper = value.toUpperCase(Locale.ROOT)
                .replace("PEBBLEOS", "PEBBLE OS")
                .replace("WATCHFACE", "FACE");
        return upper.length() <= 34 ? upper : upper.substring(0, 31) + "...";
    }

    private void openPbwPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream",
                "application/zip"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_PBW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_PBW || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }

        try {
            WatchfaceMetadata imported = repository.importFromUri(uri);
            preferences.setSelectedWatchfaceId(imported.getStorageId());
            Toast.makeText(this, "Imported " + imported.getName(), Toast.LENGTH_SHORT).show();
            reloadCatalog();
            PebbleRuntimeService.select(this);
        } catch (IOException exception) {
            showError("Import failed: " + exception.getMessage());
        }
    }

    private void maybeRequestBackgroundSetup() {
        getWindow().getDecorView().post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS
                );
                return;
            }
            maybeShowBatteryPrompt();
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            maybeShowBatteryPrompt();
        }
    }

    private void maybeShowBatteryPrompt() {
        if (isIgnoringBatteryOptimizations()) {
            return;
        }
        SharedPreferences setup = getSharedPreferences(SETUP_PREFS, MODE_PRIVATE);
        if (setup.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)) {
            return;
        }
        setup.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Keep Pebblehertz on air?")
                .setMessage("Allow Pebblehertz to run without battery optimization. "
                        + "This keeps the rear watchface alive after the main window is closed.")
                .setPositiveButton("Allow", (dialog, which) -> requestBatteryExemption())
                .setNegativeButton("Later", null)
                .show();
    }

    private void openBackgroundSettings() {
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption();
            return;
        }
        Toast.makeText(
                this,
                "Also enable Pebblehertz in DuraSpeed and disable it in App blocker.",
                Toast.LENGTH_LONG
        ).show();
        openAppDetails();
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager manager = getSystemService(PowerManager.class);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        try {
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(request);
        } catch (RuntimeException error) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (RuntimeException error) {
            showError("Cannot open application settings");
        }
    }

    private TextView pixelText(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0, 1.0f);
        return text;
    }

    private TextView bodyText(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        text.setLineSpacing(0, 1.08f);
        return text;
    }

    private TextView pixelButton(String label, int fillColor, int textColor) {
        TextView button = pixelText(label, 13, textColor);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setMinimumHeight(dp(48));
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(interactivePanelBackground(
                fillColor,
                blend(fillColor, getColor(R.color.ink), 0.15f),
                getColor(R.color.ink)
        ));
        return button;
    }

    private TextView badge(String label, int fillColor, int textColor) {
        TextView badge = pixelText(label, 10, textColor);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), 0, dp(8), 0);
        badge.setBackground(panelBackground(fillColor, getColor(R.color.ink), dp(1)));
        badge.setMinHeight(dp(26));
        return badge;
    }

    private GradientDrawable panelBackground(int fillColor, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(2));
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private StateListDrawable interactivePanelBackground(
            int normalColor,
            int pressedColor,
            int strokeColor
    ) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                panelBackground(pressedColor, strokeColor, dp(2))
        );
        states.addState(
                new int[]{android.R.attr.state_focused},
                panelBackground(pressedColor, getColor(R.color.accent_coral), dp(3))
        );
        states.addState(
                new int[]{},
                panelBackground(normalColor, strokeColor, dp(2))
        );
        return states;
    }

    private LinearLayout.LayoutParams weightedButtonParams(int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.rightMargin = rightMargin;
        return params;
    }

    private LinearLayout.LayoutParams matchWidthWrapHeight(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int blend(int first, int second, float ratio) {
        float inverse = 1f - ratio;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * ratio),
                Math.round(Color.green(first) * inverse + Color.green(second) * ratio),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * ratio)
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
