// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Hotlist;
import com.ubergeek42.WeechatAndroid.relay.Hotlist.HotMessage;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static androidx.core.app.NotificationCompat.Builder;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_CHILDREN;
import static androidx.core.app.NotificationCompat.MessagingStyle;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.AUTHENTICATED;
import static com.ubergeek42.WeechatAndroid.utils.Constants.NOTIFICATION_EXTRA_BUFFER_FULL_NAME;
import static com.ubergeek42.WeechatAndroid.utils.Constants.NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY;


public class Notificator {

    final private static @Root Kitty kitty = Kitty.make();

    final private static int NOTIFICATION_MAIN_ID = 42;
    final private static int NOTIFICATION_HOT_ID = 43;
    final private static String NOTIFICATION_CHANNEL_CONNECTION_STATUS = "connection status";
    final private static String NOTIFICATION_CHANNEL_HOTLIST = "notification";
    final private static String NOTIFICATION_CHANNEL_HOTLIST_ASYNC = "notification async";
    final private static String GROUP_KEY = "hot messages";

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static NotificationManager manager;

    @MainThread public static void init(Context c) {
        context = c.getApplicationContext();
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        // why not IMPORTANCE_MIN? it should not be used with startForeground. the docs say,
        // If you do this, as of Android version O, the system will show a higher-priority
        // notification about your app running in the background.
        // the user can manually hide the icon by setting the importance to low
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_CONNECTION_STATUS,
                context.getString(R.string.notification_channel_connection_status),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_HOTLIST,
                context.getString(R.string.notification_channel_hotlist),
                NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(true);
        manager.createNotificationChannel(channel);

        // channel for updating the notifications *silently*
        // it seems that you have to use IMPORTANCE_DEFAULT, else the notification light won't work
        channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_HOTLIST_ASYNC,
                context.getString(R.string.notification_channel_hotlist_async),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(true);
        manager.createNotificationChannel(channel);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread @Cat static void showMain(@NonNull RelayService relay, @NonNull String content) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        boolean authenticated = relay.state.contains(AUTHENTICATED);
        int icon = authenticated ? R.drawable.ic_connected : R.drawable.ic_disconnected;

        Builder builder = new Builder(context, NOTIFICATION_CHANNEL_CONNECTION_STATUS);
        builder.setContentIntent(contentIntent)
                .setSmallIcon(icon)
                .setContentTitle("Weechat-Android " + BuildConfig.VERSION_NAME)
                .setContentText(content)
                .setWhen(System.currentTimeMillis());

        builder.setPriority(Notification.PRIORITY_MIN);

        if (P.notificationTicker)
            builder.setTicker(content);

        String disconnectText = context.getString(authenticated ? R.string.disconnect : R.string.stop_connecting);

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

    @SuppressWarnings("unused")
    private static final int BUFFER = 0, LINE = 1;

    // display a notification with a hot message. clicking on it will open the buffer & scroll up
    // to the hot line, if needed. mind that SOMETIMES hotCount will be larger than hotList, because
    // it's filled from hotlist data and hotList only contains lines that arrived in real time. so
    // we add (message not available) if there are NO lines to display and add "..." if there are
    // some lines to display, but not all
    @AnyThread @Cat public static void showHot(boolean connected, int totalHotCount, int hotBufferCount,
                    List<Hotlist.HotMessage> allMessages, Hotlist.HotBuffer hotBuffer, boolean newHighlight) {
        if (!P.notificationEnable) return;

        // https://developer.android.com/guide/topics/ui/notifiers/notifications.html#back-compat
        boolean canMakeBundledNotifications = Build.VERSION.SDK_INT >= 24;
        Resources res = context.getResources();

        int hotCount = hotBuffer.hotCount;
        List<HotMessage> messages = hotBuffer.messages;
        String fullName = hotBuffer.fullName;
        String shortName = hotBuffer.shortName;

        if (hotCount == 0) {
            manager.cancel(fullName, NOTIFICATION_HOT_ID);
            DismissResult dismissResult = onNotificationDismissed(fullName);
            if (dismissResult == DismissResult.ALL_NOTIFICATIONS_REMOVED || dismissResult == DismissResult.NO_CHANGE) return;
        }

        String channel = newHighlight ? NOTIFICATION_CHANNEL_HOTLIST : NOTIFICATION_CHANNEL_HOTLIST_ASYNC;

        ////////////////////////////////////////////////////////////////////////////////////////////

        // (re)build the “parent” summary notification. in practice, it should never be visible on
        // Lollipop and later, except for the SubText part, which appears on the right of app name

        String appName = res.getString(R.string.app_name);
        String nMessagesInNBuffers = res.getQuantityString(R.plurals.messages, totalHotCount, totalHotCount) +
                res.getQuantityString(R.plurals.in_buffers, hotBufferCount, hotBufferCount);
        Builder summary = new Builder(context, channel)
                .setContentIntent(getIntentFor(NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY))
                .setSmallIcon(R.drawable.ic_hot)
                .setContentTitle(appName)
                .setContentText(nMessagesInNBuffers)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(GROUP_ALERT_CHILDREN);

        if (canMakeBundledNotifications) {
            summary.setSubText(nMessagesInNBuffers);
        } else {
            MessagingStyle style = new MessagingStyle("");
            style.setConversationTitle(nMessagesInNBuffers);
            addMissingMessageLine(totalHotCount - allMessages.size(), res, style);
            for (HotMessage message : allMessages) style.addMessage(message.forFullList());
            summary.setStyle(style);

            if (newHighlight) makeNoise(summary, res, allMessages);
        }

        manager.notify(NOTIFICATION_HOT_ID, summary.build());

        if (hotCount == 0) return;
        if (!canMakeBundledNotifications) return;

        ////////////////////////////////////////////////////////////////////////////////////////////

        String newMessageInB = res.getQuantityString(R.plurals.hot_messages, hotCount, hotCount, shortName);
        Builder builder = new Builder(context, channel)
                .setContentIntent(getIntentFor(fullName))
                .setSmallIcon(R.drawable.ic_hot)
                .setContentTitle(appName)
                .setContentText(newMessageInB)
                .setDeleteIntent(getDismissIntentFor(fullName))
                .setGroup(GROUP_KEY)
                .setWhen(hotBuffer.lastMessageTimestamp)
                .setGroupAlertBehavior(GROUP_ALERT_CHILDREN);

        // messages hold the latest messages, don't show the reply button if user can't see any
        if (connected && messages.size() > 0) builder.addAction(getAction(context, fullName));

        MessagingStyle style = new MessagingStyle("");
        style.setConversationTitle(hotCount < 2 ? shortName : shortName + " (" + String.valueOf(hotCount) + ")");
        addMissingMessageLine(hotCount - messages.size(), res, style);
        for (HotMessage message : messages) style.addMessage(message.forBuffer());
        builder.setStyle(style);

        if (newHighlight) makeNoise(builder, res, messages);
        manager.notify(fullName, NOTIFICATION_HOT_ID, builder.build());
        onNotificationFired(fullName);
    }

    // setting action in this way is not quite a proper way, but this ensures that all intents
    // are treated as separate intents
    private static PendingIntent getIntentFor(String fullName) {
        Intent intent = new Intent(context, WeechatActivity.class).putExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME, fullName);
        intent.setAction(fullName);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent getDismissIntentFor(String fullName) {
        Intent intent = new Intent(context, NotificationDismissedReceiver.class);
        intent.setAction(fullName);
        return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void addMissingMessageLine(int missingMessages, Resources res, MessagingStyle style) {
        if (missingMessages > 0) {
            String s = res.getQuantityString(R.plurals.hot_messages_missing, missingMessages, missingMessages);
            style.addMessage(s, 0, "");
        }
    }

    private static void makeNoise(Builder builder, Resources res, List<HotMessage> messages) {
        if (P.notificationTicker) builder.setTicker(messages.size() == 0 ?
                res.getQuantityString(R.plurals.hot_messages_missing, 1) :
                messages.get(messages.size() - 1).forTicker());
        builder.setPriority(Notification.PRIORITY_HIGH);
        if (!TextUtils.isEmpty(P.notificationSound)) builder.setSound(Uri.parse(P.notificationSound));
        int flags = 0;
        if (P.notificationLight) flags |= Notification.DEFAULT_LIGHTS;
        if (P.notificationVibrate) flags |= Notification.DEFAULT_VIBRATE;
        builder.setDefaults(flags);
    }

    public static final String KEY_TEXT_REPLY = "key_text_reply";
    private static NotificationCompat.Action getAction(Context ctx, String fullName) {
        String replyLabel = ctx.getResources().getString(R.string.reply_label);
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build();
        Intent intent = new Intent(ctx, Hotlist.InlineReplyReceiver.class);
        intent.setAction(fullName);
        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(ctx,
                        1,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Action.Builder(R.drawable.ic_send,
                replyLabel, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static Set<String> notifications = new HashSet<>();

    @AnyThread private static synchronized void onNotificationFired(String fullName) {
        notifications.add(fullName);
    }

    private enum DismissResult {NO_CHANGE, ONE_NOTIFICATION_REMOVED, ALL_NOTIFICATIONS_REMOVED}
    @AnyThread private static synchronized DismissResult onNotificationDismissed(String fullName) {
        boolean removed = notifications.remove(fullName);
        if (notifications.isEmpty()) {
            manager.cancel(NOTIFICATION_HOT_ID);
            return DismissResult.ALL_NOTIFICATIONS_REMOVED;
        }
        return removed ? DismissResult.ONE_NOTIFICATION_REMOVED : DismissResult.NO_CHANGE;
    }

    public static class NotificationDismissedReceiver extends BroadcastReceiver {
        @MainThread @Override public void onReceive(Context context, Intent intent) {
            final String fullName = intent.getAction();
            onNotificationDismissed(fullName);
        }
    }
}
