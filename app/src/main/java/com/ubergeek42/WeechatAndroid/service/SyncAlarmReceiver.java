package com.ubergeek42.WeechatAndroid.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.ubergeek42.WeechatAndroid.relay.BufferList;


public class SyncAlarmReceiver extends BroadcastReceiver {
    final private static int SYNC_EVERY_MS = 60 * 5 * 1000; // 5 minutes

    @WorkerThread public static void start(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, SyncAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + SYNC_EVERY_MS, SYNC_EVERY_MS, pi);
    }

    @AnyThread public static void stop(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pe = PendingIntent.getBroadcast(context, 0, new Intent(context, SyncAlarmReceiver.class),
                PendingIntent.FLAG_IMMUTABLE);
        if (am != null && pe != null) am.cancel(pe);
    }

    @MainThread @Override public void onReceive(Context context, Intent intent) {
        boolean authenticated = RelayService.staticState.contains(RelayService.STATE.AUTHENTICATED);
        if (authenticated) {
            BufferList.syncHotlist();
        } else {
            stop(context);
        }
    }
}
