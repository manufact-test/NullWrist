package com.manufact.pixelouteronly;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class ToggleTile extends TileService {
    @Override
    public void onStartListening() {
        Fold.watch(getApplicationContext(), null);
        Tile t = getQsTile();
        if (t == null) return;
        if (!Fold.supported(this)) {
            t.setState(Tile.STATE_UNAVAILABLE);
            t.setSubtitle(getString(R.string.tile_unsupported));
        } else {
            t.setState(Fold.forcedOuter ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setSubtitle(getString(Fold.forcedOuter ? R.string.tile_on : R.string.tile_off));
        }
        t.updateTile();
    }

    @Override
    public void onClick() {
        // Device-state requests must come from the top app, so bounce through the activity.
        PendingIntent pi = PendingIntent.getActivity(this, 1,
                new Intent(this, ToggleActivity.class).putExtra(ToggleActivity.EXTRA_WANT_OUTER, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        startActivityAndCollapse(pi);
    }
}
