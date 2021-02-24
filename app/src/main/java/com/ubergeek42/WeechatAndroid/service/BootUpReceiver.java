package com.ubergeek42.WeechatAndroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;


public class BootUpReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        if (!"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_BOOT_CONNECT, PREF_BOOT_CONNECT_D)) {
            RelayService.startWithAction(context, RelayService.ACTION_START);
        }
    }
}
