package com.ubergeek42.WeechatAndroid.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ubergeek42.WeechatAndroid.relay.BufferList;

public class SyncAlarmReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        if (!BufferList.syncHotlist())
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).
                    cancel(PendingIntent.getBroadcast(context, 0, intent, 0));
    }
}
