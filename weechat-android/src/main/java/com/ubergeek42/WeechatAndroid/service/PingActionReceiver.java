/*******************************************************************************
 * Copyright 2014 Matthew Horan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.service;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.Manifest;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingActionReceiver extends BroadcastReceiver {
    private static Logger logger = LoggerFactory.getLogger("PingActionReceiver");

    private volatile long lastMessageReceivedAt = 0;
    private final RelayService bone;
    private final AlarmManager alarmManager;
    private static final String PING_ACTION = BuildConfig.APPLICATION_ID + ".PING_ACTION";
    private static final IntentFilter FILTER = new IntentFilter(PING_ACTION);

    public PingActionReceiver(RelayService bone) {
        super();
        this.bone = bone;
        this.alarmManager = (AlarmManager) bone.getSystemService(Context.ALARM_SERVICE);
    }

    @MainThread @Override public void onReceive(Context context, Intent intent) {
        logger.debug("onReceive(...), sent ping? {}", intent.getBooleanExtra("sentPing", false));

        if (!bone.state.contains(AUTHENTICATED))
            return;

        long triggerAt;
        Bundle extras = new Bundle();

        if (SystemClock.elapsedRealtime() - lastMessageReceivedAt > P.pingIdleTime) {
            if (!intent.getBooleanExtra("sentPing", false)) {
                logger.debug("last message too old, sending ping");
                bone.connection.sendMessage("ping");
                triggerAt = SystemClock.elapsedRealtime() + P.pingTimeout;
                extras.putBoolean("sentPing", true);
            } else {
                logger.info("no message received, disconnecting");
                bone.stop();
                return;
            }
        } else {
            triggerAt = lastMessageReceivedAt + P.pingIdleTime;
        }

        schedulePing(triggerAt, extras);
    }

    public void scheduleFirstPing() {
        if (!P.pingEnabled) return;
        bone.registerReceiver(this, FILTER, Manifest.permission.PING_ACTION, null);
        long triggerAt = SystemClock.elapsedRealtime() + P.pingTimeout;
        schedulePing(triggerAt, new Bundle());
    }

    public void unschedulePing() {
        Intent intent = new Intent(PING_ACTION);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(bone, 0, intent, PendingIntent.FLAG_NO_CREATE);
        alarmManager.cancel(alarmIntent);
        bone.unregisterReceiver(this);
    }

    public void onMessage() {
        lastMessageReceivedAt = SystemClock.elapsedRealtime();
    }

    @TargetApi(19) private void schedulePing(long triggerAt, @NonNull Bundle extras) {
        Intent intent = new Intent(PING_ACTION);
        intent.putExtras(extras);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(bone, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        }
    }
}
