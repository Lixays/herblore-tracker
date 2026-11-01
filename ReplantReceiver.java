package com.example.herbloretracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Restart all" on the notification and "Replant all" on the widget. Runs without opening the app. */
public class ReplantReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.example.herbloretracker.REPLANT";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            HerbNotifier.replant(context);
        } catch (Throwable t) {
            HerbStore.logError(context, "replant", t);
        }
    }
}
