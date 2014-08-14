package com.ubergeek42.WeechatAndroid.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.ubergeek42.WeechatAndroid.REMOVE_ALL_SAVED_HIGHLIGHTS")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString("previous_highlights", "").commit();
        }
    }
}
