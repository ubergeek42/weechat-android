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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import com.ubergeek42.WeechatAndroid.BuildConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class PingActionReceiver extends BroadcastReceiver {
    private static Logger logger = LoggerFactory.getLogger("PingActionReceiver");

    private RelayServiceBackbone bone;
    private AlarmManager alarmManager;
    public static final String PING_ACTION = BuildConfig.APPLICATION_ID + ".PING_ACTION";

    public PingActionReceiver(RelayServiceBackbone bone) {
        super();
        this.bone = bone;
        this.alarmManager = (AlarmManager) bone.getSystemService(Context.ALARM_SERVICE);
    }

    @MainThread @Override public void onReceive(Context context, Intent intent) {
        logger.debug("onReceive(intent: {}, sentPing: {})", intent, intent.getBooleanExtra("sentPing", false));

        if (!bone.isConnection(RelayService.CONNECTED))
            return;

        long triggerAt;
        Bundle extras = new Bundle();

        if (SystemClock.elapsedRealtime() - bone.lastMessageReceivedAt > pingIdleTime()) {
            if (!intent.getBooleanExtra("sentPing", false)) {
                logger.debug("last message too old, sending ping");
                bone.connection.sendMessage("ping");
                triggerAt = SystemClock.elapsedRealtime() + pingTimeout();
                extras.putBoolean("sentPing", true);
            } else {
                logger.debug("no message received, disconnecting");
                bone.startThreadedDisconnect(false);
                return;
            }
        } else {
            triggerAt = bone.lastMessageReceivedAt + pingIdleTime();
        }

        schedulePing(triggerAt, extras);
    }

    public void scheduleFirstPing() {
        long triggerAt = SystemClock.elapsedRealtime() + pingTimeout();
        schedulePing(triggerAt, new Bundle());
    }

    public void unschedulePing() {
        Intent intent = new Intent(PingActionReceiver.PING_ACTION);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(bone, 0, intent, PendingIntent.FLAG_NO_CREATE);
        alarmManager.cancel(alarmIntent);
    }

    @TargetApi(19) private void schedulePing(long triggerAt, @NonNull Bundle extras) {
        Intent intent = new Intent(PingActionReceiver.PING_ACTION);
        intent.putExtras(extras);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(bone, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        }
    }

    private long pingIdleTime() {
        return Integer.parseInt(bone.prefs.getString(PREF_PING_IDLE, PREF_PING_IDLE_D)) * 1000;
    }

    private long pingTimeout() {
        return Integer.parseInt(bone.prefs.getString(PREF_PING_TIMEOUT, PREF_PING_TIMEOUT_D)) * 1000;
    }
}
