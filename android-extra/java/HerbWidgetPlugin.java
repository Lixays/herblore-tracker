package com.example.herbloretracker;

import android.content.Context;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridge between the web app and the Android side (notifications + widget).
 *  - update({ timers, windows }) : saves the timers, reschedules notifications, redraws the widget
 *  - getState()                  : timers + whether they were changed by a notification/widget button
 *  - clearDirty()                : marks those changes as picked up
 */
@CapacitorPlugin(name = "HerbWidget")
public class HerbWidgetPlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        Context context = getContext();
        HerbStore.saveTimersJson(context, call.getString("timers", "[]"));
        HerbStore.saveWindowsJson(context, call.getString("windows", "[9,29,49]"));
        HerbNotifier.scheduleAll(context);
        HerbWidgetProvider.updateAll(context);
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        Context context = getContext();
        JSObject result = new JSObject();
        result.put("dirty", HerbStore.isDirty(context));
        result.put("timers", HerbStore.timersJson(context));
        call.resolve(result);
    }

    @PluginMethod
    public void clearDirty(PluginCall call) {
        HerbStore.setDirty(getContext(), false);
        call.resolve();
    }
}
