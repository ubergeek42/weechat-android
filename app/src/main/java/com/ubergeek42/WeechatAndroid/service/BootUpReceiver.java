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
            Intent i = new Intent(context, RelayService.class);
            i.setAction(RelayService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // https://stackoverflow.com/a/47654126/1449683
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        }
    }
}
