package com.example.herbloretracker;

import android.content.Context;
import android.content.Intent;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridge between the web app and the home-screen widget.
 *  - update({ timers })  : saves the timers for the widget and redraws it
 *  - moveToBack()        : sends the app back to the home screen
 *  - event "replantRequested" : fired when the widget's Replant button is tapped
 */
@CapacitorPlugin(name = "HerbWidget")
public class HerbWidgetPlugin extends Plugin {

    @Override
    public void load() {
        if (getActivity() != null) handleIntent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && HerbWidgetProvider.ACTION_REPLANT.equals(intent.getAction())) {
            intent.setAction(Intent.ACTION_MAIN);   // so it is only handled once
            notifyListeners("replantRequested", new JSObject(), true);
        }
    }

    @PluginMethod
    public void update(PluginCall call) {
        String timers = call.getString("timers", "[]");
        Context context = getContext();
        context.getSharedPreferences(HerbWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit().putString("timers", timers).apply();
        HerbWidgetProvider.updateAll(context);
        call.resolve();
    }

    @PluginMethod
    public void moveToBack(PluginCall call) {
        if (getActivity() != null) getActivity().moveTaskToBack(true);
        call.resolve();
    }
}
