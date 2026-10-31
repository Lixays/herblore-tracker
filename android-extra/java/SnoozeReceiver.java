package com.example.herbloretracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Snooze 10 min" on the ready notification. Runs without opening the app. */
public class SnoozeReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.example.herbloretracker.SNOOZE";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            HerbNotifier.snooze(context);
        } catch (Throwable t) {
            HerbStore.logError(context, "snooze", t);
        }
    }
}
