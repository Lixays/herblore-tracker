package com.example.herbloretracker;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HerbWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
