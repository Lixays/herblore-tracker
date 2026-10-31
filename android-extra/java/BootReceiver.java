package com.example.herbloretracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are lost when the phone restarts or the app is updated, so set them up again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HerbNotifier.scheduleAll(context);
        HerbWidgetProvider.updateAll(context);
    }
}
