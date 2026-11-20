package com.manufact.pixelouteronly;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;

/**
 * Invisible trampoline that performs one toggle. Launched by the notification, the Quick
 * Settings tile, the launcher shortcut, or automation apps (see README for intent extras).
 */
public class ToggleActivity extends Activity {
    /** Optional boolean: true = outer screen, false = inner. Omitted = toggle. */
    static final String EXTRA_WANT_OUTER = "wantOuter";
    /** Optional int: manual device-state override for devices detection gets wrong. */
    static final String EXTRA_STATE = "state";
    /** Optional boolean: true = skip the hidden API and use the WindowExtensions path (testing). */
    static final String EXTRA_USE_EXTENSIONS = "useExtensions";
    private boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Fold.watch(getApplicationContext(), null); // current state arrives before window focus
        if (getIntent().hasExtra(EXTRA_STATE)) {
            Fold.target = getIntent().getIntExtra(EXTRA_STATE, Fold.target);
            Fold.source = "manual override";
        }
        // Per-launch only: a test/automation launch must not change later plain toggles.
        Fold.forceExtensions = getIntent().getBooleanExtra(EXTRA_USE_EXTENSIONS, false);
        if (Fold.target < 0) {
            Toast.makeText(getApplicationContext(), R.string.unsupported_toast, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            done = true;
            finishQuietly();
            return;
        }
        // Safety net: never linger if focus never arrives (e.g. screen off).
        getWindow().getDecorView().postDelayed(() -> { if (!done) finishQuietly(); }, 3000);
    }

    // The server only accepts app requests from the focused top app, so wait for window focus.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        try {
            boolean wantOuter = getIntent().getBooleanExtra(EXTRA_WANT_OUTER, !Fold.forcedOuter);
            if (wantOuter && !Fold.forcedOuter) {
                Fold.forceOuter(this);
            } else if (!wantOuter && Fold.forcedOuter) {
                Fold.release(this);
            }
        } catch (Throwable t) {
            Throwable c = t instanceof InvocationTargetException ? t.getCause() : t;
            Log.e(Fold.TAG, "toggle failed", c);
            Fold.path = "FAILED: " + c;
            Toast.makeText(getApplicationContext(), R.string.toggle_failed, Toast.LENGTH_LONG).show();
        }
        HoldService.start(this); // refresh notification text
        TileService.requestListeningState(this, new ComponentName(this, ToggleTile.class));
        finishQuietly();
    }

    private void finishQuietly() {
        finish();
        overridePendingTransition(0, 0);
    }
}
