package com.example.herbloretracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

/** Saved timers + farming tick, shared by the app, the notifications and the widget. */
final class HerbStore {
    static final String PREFS = "herb_widget";

    private HerbStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String timersJson(Context c) {
        return prefs(c).getString("timers", "[]");
    }

    static JSONArray timers(Context c) {
        try {
            return new JSONArray(timersJson(c));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Timers sent by the web app. Does not touch the "changed natively" flag. */
    static void saveTimersJson(Context c, String json) {
        prefs(c).edit().putString("timers", json).apply();
    }

    /** Timers changed natively (replant button). The web app picks them up next time it looks. */
    static void saveTimersFromNative(Context c, JSONArray timers) {
        prefs(c).edit().putString("timers", timers.toString()).putBoolean("dirty", true).apply();
    }

    static boolean isDirty(Context c) {
        return prefs(c).getBoolean("dirty", false);
    }

    static void setDirty(Context c, boolean dirty) {
        prefs(c).edit().putBoolean("dirty", dirty).apply();
    }

    // ---- diagnostics (shown in the app's settings screen) ----
    static void logError(Context c, String where, Throwable t) {
        prefs(c).edit().putString("last_error", where + ": " + t).apply();
    }

    static String lastError(Context c) {
        return prefs(c).getString("last_error", "");
    }

    static void setAlarmCount(Context c, int n) {
        prefs(c).edit().putInt("alarm_count", n).apply();
    }

    static int alarmCount(Context c) {
        return prefs(c).getInt("alarm_count", 0);
    }

    static void setLastFired(Context c, long t) {
        prefs(c).edit().putLong("last_fired", t).apply();
    }

    static long lastFired(Context c) {
        return prefs(c).getLong("last_fired", 0);
    }

    static void saveWindowsJson(Context c, String json) {
        prefs(c).edit().putString("windows", json).apply();
    }

    /** Minutes past the hour when herb patches tick, sorted. */
    static int[] windows(Context c) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString("windows", "[9,29,49]"));
            if (a.length() == 3) {
                int[] w = new int[]{a.getInt(0), a.getInt(1), a.getInt(2)};
                java.util.Arrays.sort(w);
                return w;
            }
        } catch (Exception ignored) { }
        return new int[]{9, 29, 49};
    }
}
