package com.example.herbloretracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything notification-related lives here, so it works with the app closed:
 *  - schedules a "ready" notification for each finish time
 *  - shows the "timers running" icon
 *  - replants from the notification / widget button without opening the app
 */
final class HerbNotifier {
    static final String CH_READY = "herb-timers";
    static final String CH_RUNNING = "herb-status";
    private static final String CH_RUNNING_OLD = "herb-running";
    static final int RUNNING_ID = 1000;
    static final int MAX_GROUPS = 12;
    static final long GROW_MS = 60L * 60L * 1000L;
    static final long SNOOZE_MS = 10L * 60L * 1000L;
    static final int SNOOZE_NOTIF_ID = 20;
    static final int SNOOZE_ALARM_CODE = 200;

    static final String EXTRA_AT = "at";
    static final String EXTRA_NAMES = "names";
    static final String EXTRA_ID = "id";

    private HerbNotifier() { }

    // ---------- helpers ----------

    static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ready = new NotificationChannel(CH_READY, "Herb timers", NotificationManager.IMPORTANCE_HIGH);
        ready.setDescription("Alerts when herb patches are ready");
        ready.enableVibration(true);
        nm.createNotificationChannel(ready);

        // Always-on status icon: normal importance so the icon always shows in the status bar, but silent
        nm.deleteNotificationChannel(CH_RUNNING_OLD);
        NotificationChannel status = new NotificationChannel(CH_RUNNING, "Timer status icon", NotificationManager.IMPORTANCE_DEFAULT);
        status.setDescription("Shows an icon in the status bar while herb timers are growing or ready");
        status.setSound(null, null);
        status.enableVibration(false);
        status.setShowBadge(false);
        nm.createNotificationChannel(status);
    }

    /** Next farming tick strictly after `fromMs` (same rule as the web app). */
    static long nextTick(long fromMs, int[] windows) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        int minute = cal.get(Calendar.MINUTE);
        int target = windows[0];
        boolean found = false;
        for (int w : windows) {
            if (w > minute) {
                target = w;
                found = true;
                break;
            }
        }
        if (!found) cal.add(Calendar.HOUR_OF_DAY, 1);
        cal.set(Calendar.MINUTE, target);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    static void setAlarm(AlarmManager alarms, long when, PendingIntent pending) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            }
        } catch (SecurityException e) {
            alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    static PendingIntent openAppIntent(Context ctx) {
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (intent == null) intent = new Intent(ctx, MainActivity.class);
        return PendingIntent.getActivity(ctx, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent readyAlarmIntent(Context ctx, int id, long at, String names, int flags) {
        Intent intent = new Intent(ctx, ReadyReceiver.class);
        intent.setAction("com.example.herbloretracker.READY");
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_AT, at);
        intent.putExtra(EXTRA_NAMES, names);
        return PendingIntent.getBroadcast(ctx, 100 + id, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notifySafely(Context ctx, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification);
        } catch (SecurityException ignored) { }
    }

    // ---------- scheduling ----------

    /** Rebuilds every "ready" alarm from the saved timers, then refreshes the running icon. */
    static void scheduleAll(Context ctx) {
        try {
            scheduleAlarms(ctx);
        } catch (Throwable t) {
            HerbStore.logError(ctx, "alarms", t);
        }
        try {
            updateRunning(ctx);
        } catch (Throwable t) {
            HerbStore.logError(ctx, "status icon", t);
        }
    }

    private static void scheduleAlarms(Context ctx) {
        ensureChannels(ctx);
        AlarmManager alarms = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;

        // Cancel the old alarms
        for (int i = 1; i <= MAX_GROUPS; i++) {
            PendingIntent old = readyAlarmIntent(ctx, i, 0, "", PendingIntent.FLAG_NO_CREATE);
            if (old != null) {
                alarms.cancel(old);
                old.cancel();
            }
        }

        // Group patches that finish at the same moment
        long now = System.currentTimeMillis();
        JSONArray timers = HerbStore.timers(ctx);
        TreeMap<Long, ArrayList<String>> groups = new TreeMap<>();
        try {
            for (int i = 0; i < timers.length(); i++) {
                JSONObject t = timers.getJSONObject(i);
                long finish = t.optLong("finish", 0);
                if (finish > now) {
                    ArrayList<String> names = groups.get(finish);
                    if (names == null) {
                        names = new ArrayList<>();
                        groups.put(finish, names);
                    }
                    names.add(t.optString("name", "Patch"));
                }
            }
        } catch (Exception ignored) { }

        int id = 1;
        for (Map.Entry<Long, ArrayList<String>> entry : groups.entrySet()) {
            if (id > MAX_GROUPS) break;
            ArrayList<String> list = entry.getValue();
            String names;
            if (list.size() == timers.length()) {
                names = "All patches";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(list.get(i));
                }
                names = sb.toString();
            }
            PendingIntent pending = readyAlarmIntent(ctx, id, entry.getKey(), names, PendingIntent.FLAG_UPDATE_CURRENT);
            setAlarm(alarms, entry.getKey(), pending);
            id++;
        }
        HerbStore.setAlarmCount(ctx, id - 1);
    }

    /** True when alarms will fire exactly on time. */
    static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarms = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    // ---------- notifications ----------

    /** The "ready to harvest" notification with its Restart all button. */
    static void showReady(Context ctx, int id, long at, String names) {
        ensureChannels(ctx);
        long restartDone = nextTick(at, HerbStore.windows(ctx)) + GROW_MS;
        String when = clock(restartDone);

        Intent replant = new Intent(ctx, ReplantReceiver.class);
        replant.setAction(ReplantReceiver.ACTION);
        PendingIntent replantPending = PendingIntent.getBroadcast(ctx, 50, replant,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(ctx, SnoozeReceiver.class);
        snoozeIntent.setAction(SnoozeReceiver.ACTION);
        PendingIntent snoozePending = PendingIntent.getBroadcast(ctx, 60, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CH_READY)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle("Herbs ready to harvest!")
                .setContentText(names + " \u00B7 restart to be done at " + when)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        names + " ready to harvest.\nRestart now and they'll next be done at " + when + "."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(ctx))
                .addAction(0, "Restart all", replantPending)
                .addAction(0, "Snooze 10 min", snoozePending);
        notifySafely(ctx, id, builder.build());
    }

    /** Names of the patches that are finished right now, or null if none are. */
    static String readyNames(Context ctx) {
        long now = System.currentTimeMillis();
        JSONArray timers = HerbStore.timers(ctx);
        ArrayList<String> names = new ArrayList<>();
        try {
            for (int i = 0; i < timers.length(); i++) {
                JSONObject t = timers.getJSONObject(i);
                long finish = t.optLong("finish", 0);
                if (finish > 0 && finish <= now) names.add(t.optString("name", "Patch"));
            }
        } catch (Exception ignored) { }
        if (names.isEmpty()) return null;
        if (names.size() == timers.length()) return "All patches";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static PendingIntent snoozeAlarmIntent(Context ctx) {
        Intent intent = new Intent(ctx, ReadyReceiver.class);
        intent.setAction("com.example.herbloretracker.SNOOZED");
        intent.putExtra("snoozed", true);
        return PendingIntent.getBroadcast(ctx, SNOOZE_ALARM_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Hides the "ready" notifications and brings them back in 10 minutes. */
    static void snooze(Context ctx) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        for (int i = 1; i <= MAX_GROUPS; i++) nm.cancel(i);
        nm.cancel(SNOOZE_NOTIF_ID);

        AlarmManager alarms = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = snoozeAlarmIntent(ctx);
        alarms.cancel(pending);
        setAlarm(alarms, System.currentTimeMillis() + SNOOZE_MS, pending);
    }

    /** Forget any snoozed reminder (used when the timers are replanted). */
    static void cancelSnooze(Context ctx) {
        AlarmManager alarms = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(snoozeAlarmIntent(ctx));
        NotificationManagerCompat.from(ctx).cancel(SNOOZE_NOTIF_ID);
    }

    /**
     * Status-bar icon. Shown while any timer is growing AND while finished patches are waiting
     * to be harvested. Removed only when no patch is planted.
     */
    static void updateRunning(Context ctx) {
        ensureChannels(ctx);
        long now = System.currentTimeMillis();
        int running = 0;
        int ready = 0;
        long next = 0;
        JSONArray timers = HerbStore.timers(ctx);
        try {
            for (int i = 0; i < timers.length(); i++) {
                long finish = timers.getJSONObject(i).optLong("finish", 0);
                if (finish <= 0) continue;
                if (finish > now) {
                    running++;
                    if (next == 0 || finish < next) next = finish;
                } else {
                    ready++;
                }
            }
        } catch (Exception ignored) { }

        if (running == 0 && ready == 0) {
            NotificationManagerCompat.from(ctx).cancel(RUNNING_ID);
            return;
        }

        String title;
        String text;
        if (running > 0) {
            title = "Herb timers running";
            text = running + " growing \u00B7 next ready " + clock(next);
            if (ready > 0) text += " \u00B7 " + ready + " ready";
        } else {
            title = "Herbs ready to harvest!";
            text = ready + (ready == 1 ? " patch is ready" : " patches are ready");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null)
                .setVibrate(null)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setContentIntent(openAppIntent(ctx));
        notifySafely(ctx, RUNNING_ID, builder.build());
    }

    // ---------- replant (from the notification or the widget) ----------

    /** Replants every finished patch. If nothing is planted at all, plants every patch. */
    static void replant(Context ctx) {
        long now = System.currentTimeMillis();
        long newFinish = nextTick(now, HerbStore.windows(ctx)) + GROW_MS;
        JSONArray timers = HerbStore.timers(ctx);
        try {
            boolean anyPlanted = false;
            for (int i = 0; i < timers.length(); i++) {
                if (timers.getJSONObject(i).optLong("finish", 0) > 0) anyPlanted = true;
            }
            for (int i = 0; i < timers.length(); i++) {
                JSONObject t = timers.getJSONObject(i);
                long finish = t.optLong("finish", 0);
                if (!anyPlanted || (finish > 0 && finish <= now)) {
                    t.put("finish", newFinish);
                }
            }
            HerbStore.saveTimersFromNative(ctx, timers);
        } catch (Exception ignored) { }

        // Clear the "ready" notifications that were showing
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        for (int i = 1; i <= MAX_GROUPS; i++) nm.cancel(i);
        cancelSnooze(ctx);

        scheduleAll(ctx);
        HerbWidgetProvider.updateAll(ctx);
    }
}
