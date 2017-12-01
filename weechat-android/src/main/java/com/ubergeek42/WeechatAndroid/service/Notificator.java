/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Notificator {

    private static Logger logger = LoggerFactory.getLogger("Notificator");
    final private static boolean DEBUG_NOTIFICATIONS = false;

    final private static int NOTIFICATION_MAIN_ID = 42;
    final private static int NOTIFICATION_HOT_ID = 43;
    final private static String NOTIFICATION_CHANNEL_CONNECTION_STATUS = "connection status";
    final private static String NOTIFICATION_CHANNEL_HOTLIST = "notification";

    private static Context context;
    private static NotificationManager manager;

    public static void init(Context c) {
        context = c.getApplicationContext();
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static void showMain(@NonNull RelayService relay, @NonNull String content, @Nullable PendingIntent intent) {
        showMain(relay, content, content, intent);
    }

    /** show the persistent notification of the service
     *
     * @param tickerText text that flashes a bit; doesn't appear on L+
     * @param content the smaller text that appears under title
     * @param intent intent that's executed on notification click, default used if null
     */
    public static void showMain(@NonNull RelayService relay, @Nullable String tickerText, @NonNull String content, @Nullable PendingIntent intent) {
        if (DEBUG_NOTIFICATIONS) logger.debug("showMain({}, {}, {})", tickerText, content, intent);

        PendingIntent contentIntent = (intent != null) ? intent :
                PendingIntent.getActivity(context, 0, new Intent(context, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        int icon = relay.state.contains(AUTHENTICATED) ? R.drawable.ic_connected : R.drawable.ic_disconnected;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_CONNECTION_STATUS,
                    context.getString(R.string.notification_channel_connection_status),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        // use application context because of a bug in android: passed context will get leaked
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_CONNECTION_STATUS);
        builder.setContentIntent(contentIntent)
                .setSmallIcon(icon)
                .setContentTitle("WeechatAndroid " + BuildConfig.VERSION_NAME)
                .setContentText(content)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builder.setPriority(Notification.PRIORITY_MIN);

        if (P.notificationTicker)
            builder.setTicker(tickerText);

        String disconnectText = context.getString(relay.state.contains(AUTHENTICATED) ? R.string.disconnect : R.string.stop_connecting);

        builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel, disconnectText,
                PendingIntent.getService(
                    context, 0,
                    new Intent(RelayService.ACTION_STOP, null, context, RelayService.class),
                    0
                )
        );

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        relay.startForeground(NOTIFICATION_MAIN_ID, notification);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final int BUFFER = 0, LINE = 1;

    /** display notification with a hot message
     ** clicking on it will open the buffer & scroll up to the hot line, if needed
     ** mind that SOMETIMES hotCount will be larger than hotList, because
     ** it's filled from hotlist data and hotList only contains lines that
     ** arrived in real time. so we add (message not available) if there are NO lines to display
     ** and add "..." if there are some lines to display, but not all */
    public static void showHot(boolean newHighlight) {
        if (DEBUG_NOTIFICATIONS) logger.debug("showHot({})", newHighlight);

        if (!P.notificationEnable)
            return;

        final int hotCount = BufferList.getHotCount();
        final List<String[]> hotList = BufferList.hotList;

        if (hotCount == 0) {
            manager.cancel(NOTIFICATION_HOT_ID);
            return;
        }

        // find our target buffer. if ALL items point to the same buffer, use it,
        // otherwise, go to buffer list (→ "")
        Set<String> set = new HashSet<>();
        for (String[] h: hotList) set.add(h[BUFFER]);
        String target_buffer = (hotCount == hotList.size() && set.size() == 1) ? hotList.get(0)[BUFFER] : "";

        // prepare intent
        Intent intent = new Intent(context, WeechatActivity.class).putExtra("full_name", target_buffer);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_HOTLIST,
                    context.getString(R.string.notification_channel_hotlist),
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        // prepare notification
        // make the ticker the LAST message
        String message = hotList.size() == 0 ? context.getString(R.string.hot_message_not_available) : hotList.get(hotList.size() - 1)[LINE];
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_HOTLIST)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_hot)
                .setContentTitle(context.getResources().getQuantityString(R.plurals.hot_messages, hotCount, hotCount))
                .setContentText(message);

        // display several lines only if we have at least one visible line and
        // 2 or more lines total. that is, either display full list of lines or
        // one ore more visible lines and "..."
        if (hotList.size() > 0 && hotCount > 1) {
            NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
                    .setSummaryText(P.printableHost);

            for (String[] bufferToLine : hotList) inbox.addLine(bufferToLine[LINE]);
            if (hotList.size() < hotCount) inbox.addLine("…");

            builder.setContentInfo(String.valueOf(hotCount));
            builder.setStyle(inbox);
        }

        if (newHighlight) {
            builder.setTicker(message);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                builder.setPriority(Notification.PRIORITY_HIGH);

            if (!TextUtils.isEmpty(P.notificationSound))
                builder.setSound(Uri.parse(P.notificationSound));

            int flags = 0;
            if (P.notificationLight) flags |= Notification.DEFAULT_LIGHTS;
            if (P.notificationVibrate) flags |= Notification.DEFAULT_VIBRATE;
            builder.setDefaults(flags);
        }

        manager.notify(NOTIFICATION_HOT_ID, builder.build());
    }
}
