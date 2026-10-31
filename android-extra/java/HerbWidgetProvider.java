package com.example.herbloretracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: shows the soonest-finishing timer and a Replant all button. */
public class HerbWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.example.herbloretracker.WIDGET_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            updateAll(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        scheduleRefresh(context, 0);
        super.onDisabled(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, HerbWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) {
            scheduleRefresh(context, 0);
            return;
        }

        long now = System.currentTimeMillis();
        long nextFinish = 0;
        String nextName = "";
        int running = 0;
        int ready = 0;
        try {
            JSONArray timers = HerbStore.timers(context);
            for (int i = 0; i < timers.length(); i++) {
                JSONObject t = timers.getJSONObject(i);
                long finish = t.optLong("finish", 0);
                if (finish <= 0) continue;                    // empty patch
                if (finish > now) {
                    running++;
                    if (nextFinish == 0 || finish < nextFinish) {
                        nextFinish = finish;
                        nextName = t.optString("name", "");
                    }
                } else {
                    ready++;
                }
            }
        } catch (Exception ignored) { }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.herb_widget);

        if (running > 0) {
            views.setTextViewText(R.id.widget_name, "Next: " + nextName);
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE);
            if (Build.VERSION.SDK_INT >= 24) {
                views.setChronometerCountDown(R.id.widget_timer, true);
            }
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + (nextFinish - now), null, true);
            String sub = "Ready at " + HerbNotifier.clock(nextFinish) + " \u00B7 " + running + " growing";
            if (ready > 0) sub += " \u00B7 " + ready + " ready";
            views.setTextViewText(R.id.widget_sub, sub);
        } else if (ready > 0) {
            views.setTextViewText(R.id.widget_name, "Ready to harvest!");
            views.setViewVisibility(R.id.widget_timer, View.GONE);
            views.setTextViewText(R.id.widget_sub, ready + (ready == 1 ? " patch is ready" : " patches are ready"));
        } else {
            views.setTextViewText(R.id.widget_name, "No timers running");
            views.setViewVisibility(R.id.widget_timer, View.GONE);
            views.setTextViewText(R.id.widget_sub, "Open the app to plant");
        }

        views.setOnClickPendingIntent(R.id.widget_replant, replantIntent(context));
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));

        manager.updateAppWidget(component, views);

        // Redraw the widget when the soonest timer finishes
        scheduleRefresh(context, running > 0 ? nextFinish : 0);
    }

    /** Replant happens in the background, the app is not opened. */
    private static PendingIntent replantIntent(Context context) {
        Intent intent = new Intent(context, ReplantReceiver.class);
        intent.setAction(ReplantReceiver.ACTION);
        return PendingIntent.getBroadcast(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, HerbWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleRefresh(Context context, long at) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = refreshIntent(context);
        alarms.cancel(pending);
        if (at <= 0) return;
        HerbNotifier.setAlarm(alarms, at + 1000, pending);
    }
}
