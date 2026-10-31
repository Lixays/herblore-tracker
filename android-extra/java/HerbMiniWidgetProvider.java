package com.example.herbloretracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** 1x1 widget: how long until ALL timers are done (the last one to finish). */
public class HerbMiniWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.example.herbloretracker.MINI_REFRESH";

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
        ComponentName component = new ComponentName(context, HerbMiniWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) {
            scheduleRefresh(context, 0);
            return;
        }

        long now = System.currentTimeMillis();
        long lastFinish = 0;
        int running = 0;
        int ready = 0;
        try {
            JSONArray timers = HerbStore.timers(context);
            for (int i = 0; i < timers.length(); i++) {
                JSONObject t = timers.getJSONObject(i);
                long finish = t.optLong("finish", 0);
                if (finish <= 0) continue;
                if (finish > now) {
                    running++;
                    if (finish > lastFinish) lastFinish = finish;   // the LAST one to finish
                } else {
                    ready++;
                }
            }
        } catch (Exception ignored) { }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.herb_widget_mini);

        if (running > 0) {
            views.setTextViewText(R.id.mini_label, "ALL DONE IN");
            views.setViewVisibility(R.id.mini_timer, View.VISIBLE);
            views.setViewVisibility(R.id.mini_big, View.GONE);
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                views.setChronometerCountDown(R.id.mini_timer, true);
            }
            views.setChronometer(R.id.mini_timer, SystemClock.elapsedRealtime() + (lastFinish - now), null, true);
            views.setTextViewText(R.id.mini_sub, "at " + HerbNotifier.clock(lastFinish));
        } else if (ready > 0) {
            views.setTextViewText(R.id.mini_label, "ALL DONE");
            views.setViewVisibility(R.id.mini_timer, View.GONE);
            views.setViewVisibility(R.id.mini_big, View.VISIBLE);
            views.setTextViewText(R.id.mini_big, "Harvest!");
            views.setTextViewText(R.id.mini_sub, ready + " ready");
        } else {
            views.setTextViewText(R.id.mini_label, "HERB TIMER");
            views.setViewVisibility(R.id.mini_timer, View.GONE);
            views.setViewVisibility(R.id.mini_big, View.VISIBLE);
            views.setTextViewText(R.id.mini_big, "Idle");
            views.setTextViewText(R.id.mini_sub, "nothing planted");
        }

        views.setOnClickPendingIntent(R.id.mini_root, HerbNotifier.openAppIntent(context));
        manager.updateAppWidget(component, views);

        // Redraw when the last timer finishes
        scheduleRefresh(context, running > 0 ? lastFinish : 0);
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, HerbMiniWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 4, intent,
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
