package com.ubergeek42.WeechatAndroid.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.greenrobot.event.EventBus;

/**
 * Created by andi on 4/11/17.
 */

public class ReplyReceiver extends BroadcastReceiver {
    private static Logger logger = LoggerFactory.getLogger("ReplyReceiver");

    final public static String ACTION_REPLY = "ACTION_REPLY";

    public ReplyReceiver() {
        super();

    }

    @Override
    public void onReceive(Context context, Intent intent)  {
        logger.error("onStartCommand");
        if (intent != null) {
            final String action = intent.getAction();
            logger.error(action);
            if (ACTION_REPLY.equals(action)) {
                doReply(context, intent);
            }
        }
    }

    private void doReply(Context context, Intent intent) {
        // Get remote input from received intent. Remote input is an answer a user has added to the reply. In our case it should contain a String.
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            logger.debug("remoteInput is null");
            return;
        }

        // Get channel

        final String bufferName = intent.getStringExtra("full_name");


        // Get reply text
        String text = remoteInput.getString(Notificator.KEY_TEXT_REPLY);
        logger.error(text);

        Buffer buffer = BufferList.findByFullName(bufferName);

        if (buffer == null) {
            logger.error("Unknown buffer");
            return;
        }


        P.addSentMessage(text);
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.length() != 0)
                EventBus.getDefault().post(new Events.SendMessageEvent(String.format("input %s %s", buffer.hexPointer(), line)));
        }


        Notification repliedNotification =
                new Notification.Builder(context)
                        .setSmallIcon(R.drawable.ic_send)
                        .setContentText("Replied")
                        .build();

        // Issue the new notification.
        Notificator.notify(Notificator.NOTIFICATION_HOT_ID, repliedNotification);

    }
}
