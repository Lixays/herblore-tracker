package com.example.herbloretracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a group of patches finishes. */
public class ReadyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getBooleanExtra("snoozed", false)) {
            // Snoozed reminder: only show it if some patches are still waiting to be harvested
            try {
                String ready = HerbNotifier.readyNames(context);
                if (ready != null) {
                    HerbNotifier.showReady(context, HerbNotifier.SNOOZE_NOTIF_ID, System.currentTimeMillis(), ready);
                }
            } catch (Throwable t) {
                HerbStore.logError(context, "snooze reminder", t);
            }
            return;
        }

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
