package com.ubergeek42.WeechatAndroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class BootUpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_AUTO_START, PREF_AUTO_START_D)) {
            Intent i = new Intent(context, RelayService.class);
            context.startService(i);
        }
    }
}
