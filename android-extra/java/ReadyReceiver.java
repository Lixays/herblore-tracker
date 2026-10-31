package com.example.herbloretracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a group of patches finishes. */
public class ReadyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(HerbNotifier.EXTRA_ID, 1);
        long at = intent.getLongExtra(HerbNotifier.EXTRA_AT, System.currentTimeMillis());
        String names = intent.getStringExtra(HerbNotifier.EXTRA_NAMES);
        if (names == null || names.isEmpty()) names = "Herb patches";

        HerbStore.setLastFired(context, System.currentTimeMillis());
        try {
            HerbNotifier.showReady(context, id, at, names);
        } catch (Throwable t) {
            HerbStore.logError(context, "ready notification", t);
        }
        try {
            HerbNotifier.updateRunning(context);
            HerbWidgetProvider.updateAll(context);
        } catch (Throwable t) {
            HerbStore.logError(context, "status icon", t);
        }
    }
}
