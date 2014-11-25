package com.ubergeek42.WeechatAndroid.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        RelayServiceBinder relay = (RelayServiceBinder) peekService(context, new Intent(context, RelayService.class));
        if (relay != null && relay.isConnection(RelayService.CONNECTED))
            BufferList.syncHotlist();
        else
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).
                    cancel(PendingIntent.getBroadcast(context, 0, intent, 0));
    }
}
