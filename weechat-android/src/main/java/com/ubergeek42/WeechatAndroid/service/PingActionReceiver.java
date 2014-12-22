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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingActionReceiver extends BroadcastReceiver {
    private static Logger logger = LoggerFactory.getLogger("PingActionReceiver");
    private RelayService relayService;
    public static final String PING_ACTION = "com.ubergeek42.WeechatAndroid.PING_ACTION";

    public PingActionReceiver(RelayService relayService) {
        super();
        this.relayService = relayService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.debug("onReceive intent: {}, sentPing: {}", intent, intent.getBooleanExtra("sentPing", false));

        if (!relayService.isConnection(RelayService.CONNECTED))
            return;

        long triggerAt;
        Bundle extras = new Bundle();

        if (SystemClock.elapsedRealtime() - relayService.lastMessageReceivedAt > 60 * 5 * 1000) {
            if (!intent.getBooleanExtra("sentPing", false)) {
                logger.debug("last message too old, sending ping");
                relayService.connection.sendMsg("ping");
                triggerAt = SystemClock.elapsedRealtime() + 30 * 1000;
                extras.putBoolean("sentPing", true);
            } else {
                logger.debug("no message received, disconnecting");
                relayService.startThreadedDisconnect(false);
                return;
            }
        } else {
            triggerAt = relayService.lastMessageReceivedAt + 60 * 5 * 1000;
        }

        relayService.schedulePing(triggerAt, extras);
    }
}
