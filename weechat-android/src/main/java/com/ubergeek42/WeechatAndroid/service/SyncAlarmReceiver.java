package com.ubergeek42.WeechatAndroid.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.ubergeek42.WeechatAndroid.relay.BufferList;

public class SyncAlarmReceiver extends BroadcastReceiver {

    final private static int SYNC_EVERY_MS = 60 * 5 * 1000; // 5 minutes

    public static void start(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SyncAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + SYNC_EVERY_MS, SYNC_EVERY_MS, pi);
    }

    public static void stop(Context context) {
        // todo
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (!BufferList.syncHotlist())
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).
                    cancel(PendingIntent.getBroadcast(context, 0, intent, 0));
    }
}
