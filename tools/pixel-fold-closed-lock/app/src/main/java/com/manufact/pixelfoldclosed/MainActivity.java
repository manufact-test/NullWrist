package com.manufact.pixelfoldclosed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ServiceConnection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

/** A user-facing shell override with explicit setup, safety reset and diagnostics. */
public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 302;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private IFoldControl remote;
    private boolean binding;
    private int pending = -1;
    private TextView state;
    private TextView instructions;
    private TextView result;
    private Button grant;
    private Button apply;
    private Button reset;
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (request, granted) -> {
            if (request == PERMISSION_REQUEST)
                runOnUiThread(() -> {
                    refresh();
                    if (granted == PackageManager.PERMISSION_GRANTED && pending >= 0) {
                        int a = pending;
                        pending = -1;
                        perform(a);
                    }
                });
        };
    private final Shizuku.OnBinderReceivedListener receivedListener =
        () -> runOnUiThread(this::refresh);
    private final Shizuku.OnBinderDeadListener deadListener = () ->
        runOnUiThread(() -> {
            remote = null;
            binding = false;
            result.setText("Shizuku остановлен. Запусти его снова для управления состоянием.");
            refresh();
        });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName component, IBinder binder) {
            remote = IFoldControl.Stub.asInterface(binder);
            binding = false;
            runOnUiThread(() -> {
                refresh();
                if (pending >= 0) {
                    int action = pending;
                    pending = -1;
                    execute(action);
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName component) {
            remote = null;
            binding = false;
            runOnUiThread(() -> {
                result.setText("Соединение с Shizuku разорвано.");
                refresh();
            });
        }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(0xff111318);
        getWindow().setNavigationBarColor(0xff111318);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(this);
        column.setPadding(dp(24), dp(30), dp(24), dp(28));
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        column.addView(text("PIXEL FOLD", 14, true, 0xff78aafc, 0));
        column.addView(text("Только внешний экран", 27, true, 0xfff5f6fc, 8));
        column.addView(text("Принудительный режим CLOSED вместо временного REAR_DISPLAY. Для Android 17.", 15, false, 0xffb5bccc, 12));
        state = text("", 18, true, 0xfff0f1f6, 26);
        column.addView(state);
        instructions = text("", 14, false, 0xffcbd0dc, 16);
        column.addView(instructions);

        Button open = button("Открыть Shizuku", column, 18);
        open.setOnClickListener(v -> openShizuku());

        grant = button("Разрешить управление через Shizuku", column, 8);
        grant.setOnClickListener(v -> {
            if (!isAvailable()) {
                openShizuku();
                return;
            }
            try { Shizuku.requestPermission(PERMISSION_REQUEST); }
            catch (Throwable t) { result.setText("Ошибка разрешения: " + t); }
        });

        apply = button("Зафиксировать CLOSED (0)", column, 18);
        apply.setOnClickListener(v -> perform(0));
        reset = button("Сбросить принудительный режим", column, 8);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Сбросить режим?")
            .setMessage("Если Pixel раскрыт, Android может снова включить разбитый внутренний экран. Лучше сначала сложить телефон.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить", (d, w) -> perform(1)).show());

        Button diagnostics = button("Проверить состояние Android", column, 18);
        diagnostics.setOnClickListener(v -> perform(2));
        Button copy = button("Скопировать результат", column, 8);
        copy.setOnClickListener(v -> {
            String t = result.getText().toString();
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Pixel Fold CLOSED diagnostics", t));
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
        });
        result = text("Диагностика ещё не запускалась.", 12, false, 0xffaeb8cb, 18);
        result.setTypeface(Typeface.MONOSPACE);
        result.setTextIsSelectable(true);
        column.addView(result);
        column.addView(text("Важно: Shizuku даёт приложению доступ к системным командам. Это не Root, но требуется сопряжение через беспроводную отладку на самом телефоне. После перезагрузки Shizuku надо запустить снова. Команда базового состояния пока не подтверждена на твоём Pixel — проверим по результату.", 12, false, 0xff9298a6, 26));
        setContentView(scroll);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListener(receivedListener);
        Shizuku.addBinderDeadListener(deadListener);
        refresh();
    }
    @Override protected void onResume() {
        super.onResume();
        refresh();
    }
    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(receivedListener);
        Shizuku.removeBinderDeadListener(deadListener);
        worker.shutdownNow();
        super.onDestroy();
    }
    private boolean isAvailable() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }
    private boolean hasPermission() {
        try { return isAvailable() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }
    private void refresh() {
        if (state == null) return;
        boolean online = isAvailable();
        boolean allowed = hasPermission();
        boolean requested = getSharedPreferences("state", Context.MODE_PRIVATE).getBoolean("requested", false);
        state.setText(!online ? "○ Shizuku не запущен" : !allowed
                ? "◐ Нужен доступ Shizuku"
                : requested ? "● CLOSED был запрошен" : "○ Можно активировать CLOSED");
        instructions.setText(!online
            ? "1. Установи Shizuku.\n2. Включи параметры разработчика и беспроводную отладку.\n3. Сопряги и запусти Shizuku прямо на телефоне.\n4. Вернись сюда."
            : !allowed ? "Shizuku работает. Теперь разреши доступ этому приложению."
            : "Нажми «Зафиксировать CLOSED» и проверь, остаётся ли внешний экран после складывания и повторного раскрытия.");
        grant.setVisibility(online && !allowed ? View.VISIBLE : View.GONE);
        apply.setEnabled(allowed);
        reset.setEnabled(allowed);
    }
    private void openShizuku() {
        try {
            Intent start = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (start != null) startActivity(start);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        } catch (Exception e) {
            result.setText("Не удалось открыть Shizuku: " + e.getMessage());
        }
    }
    private void perform(int action) {
        if (!isAvailable()) {
            result.setText("Сначала установи и запусти Shizuku.");
            refresh();
            return;
        }
        if (!hasPermission()) {
            pending = action;
            try { Shizuku.requestPermission(PERMISSION_REQUEST); }
            catch (Throwable t) { result.setText("Не удалось запросить доступ: " + t.getMessage()); }
            return;
        }
        if (remote == null) {
            pending = action;
            if (!binding) {
                binding = true;
                result.setText("Подключаюсь к системной службе…");
                try {
                    Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                        new ComponentName(this, FoldShellService.class))
                        .tag("pixel_fold_closed").processNameSuffix("foldcontrol").daemon(true).version(1);
                    Shizuku.bindUserService(args, connection);
                } catch (Throwable t) {
                    binding = false;
                    pending = -1;
                    result.setText("Не удалось подключиться: " + t.getMessage());
                }
            }
            return;
        }
        execute(action);
    }
    private void execute(int action) {
        result.setText("Проверяю ответ Android…");
        IFoldControl shell = remote;
        worker.execute(() -> {
            String reply;
            try { reply = shell.execute(action); }
            catch (Throwable t) { reply = "ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            String finalReply = reply;
            runOnUiThread(() -> {
                if (action == 0 && finalReply.startsWith("OK_STATE"))
                    getSharedPreferences("state", Context.MODE_PRIVATE).edit().putBoolean("requested", true).apply();
                if (action == 1 && finalReply.startsWith("OK_RESET"))
                    getSharedPreferences("state", Context.MODE_PRIVATE).edit().putBoolean("requested", false).apply();
                String prefix = action == 0 && finalReply.startsWith("OK_STATE")
                    ? "Android принял команду CLOSED. Теперь проверь раскрытие и складывание.\n\n"
                    : action == 1 && finalReply.startsWith("OK_RESET")
                    ? "Принудительный режим сброшен.\n\n" : "";
                result.setText(prefix + finalReply);
                refresh();
            });
        });
    }
    private Button button(String label, LinearLayout container, int topDp) {
        Button b = new Button(this);
        b.setText(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.topMargin = dp(topDp);
        container.addView(b, p);
        return b;
    }
    private TextView text(String s, int sp, boolean bold, int color, int top) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(top), 0, 0);
        return t;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
