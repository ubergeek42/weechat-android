package com.ubergeek42.WeechatAndroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;
import static com.ubergeek42.WeechatAndroid.utils.MultiSharedPreferencesKt.multiSharedPreferences;


public class BootUpReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        if (!"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) return;

        if (multiSharedPreferences.getBoolean(PREF_BOOT_CONNECT, PREF_BOOT_CONNECT_D)) {
            RelayService.startWithAction(context, RelayService.ACTION_START);
        }
    }
}
