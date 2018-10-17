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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
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

    // displayed in place of user name in private notifications, when we can get away with it
    final private static CharSequence ZERO_WIDTH_SPACE = "\u200B";

    // this text is somewhat displayed on android p when replying to notification
    // represents “Me” in the “Me: my message” part of NotificationCompat.MessagingStyle
    private static Person MYSELF = new Person.Builder().setName("Me").build();

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
        MYSELF = new Person.Builder().setName(c.getString(R.string.me)).build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread @Cat static void showMain(@NonNull RelayService relay, @NonNull String content) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        boolean authenticated = relay.state.contains(AUTHENTICATED);

        Builder builder = new Builder(context, NOTIFICATION_CHANNEL_CONNECTION_STATUS);
        builder.setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notification_main)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setWhen(System.currentTimeMillis())
                .setPriority(Notification.PRIORITY_MIN);
        setNotificationTitleAndText(builder, content);

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
                    List<Hotlist.HotMessage> allMessages, Hotlist.HotBuffer hotBuffer, boolean newHighlight,
                                               long lastMessageTimestamp) {
        if (!P.notificationEnable) return;

        // https://developer.android.com/guide/topics/ui/notifiers/notifications.html#back-compat
        boolean canMakeBundledNotifications = Build.VERSION.SDK_INT >= 24;
        Resources res = context.getResources();

        int hotCount = hotBuffer.hotCount;
        List<HotMessage> messages = hotBuffer.messages;
        String fullName = hotBuffer.fullName;
        String shortName = hotBuffer.shortName;

        if (hotCount == 0) {
            // TODO this doesn't cancel notifications that have remote input and have ben replied to
            // TODO on android p. not sure what to do about this--find a workaround or leave as is?
            // TODO https://issuetracker.google.com/issues/112319501
            manager.cancel(fullName, NOTIFICATION_HOT_ID);
            DismissResult dismissResult = onNotificationDismissed(fullName);
            if (dismissResult == DismissResult.ALL_NOTIFICATIONS_REMOVED || dismissResult == DismissResult.NO_CHANGE) return;
        }

        String channel = newHighlight ? NOTIFICATION_CHANNEL_HOTLIST : NOTIFICATION_CHANNEL_HOTLIST_ASYNC;

        ////////////////////////////////////////////////////////////////////////////////////////////

        // (re)build the “parent” summary notification. in practice, it should never be visible on
        // Lollipop and later, except for the SubText part, which appears on the right of app name

        String nMessagesInNBuffers = res.getQuantityString(R.plurals.messages, totalHotCount, totalHotCount) +
                res.getQuantityString(R.plurals.in_buffers, hotBufferCount, hotBufferCount);
        Builder summary = new Builder(context, channel)
                .setContentIntent(getIntentFor(NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY))
                .setSmallIcon(R.drawable.ic_notification_hot)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(lastMessageTimestamp)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(GROUP_ALERT_CHILDREN);
        setNotificationTitleAndText(summary, nMessagesInNBuffers);

        if (canMakeBundledNotifications) {
            summary.setSubText(nMessagesInNBuffers);
        } else {
            MessagingStyle style = new MessagingStyle(MYSELF);
            style.setConversationTitle(nMessagesInNBuffers);
            style.setGroupConversation(true);   // needed to display the title
            addMissingMessageLine(totalHotCount - allMessages.size(), res, style, null);
            for (HotMessage message : allMessages) style.addMessage(new MessagingStyle.Message(
                    message.message, message.timestamp, getNickForFullList(message)
            ));
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
                .setSmallIcon(R.drawable.ic_notification_hot)
                .setDeleteIntent(getDismissIntentFor(fullName))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(hotBuffer.lastMessageTimestamp)
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(GROUP_ALERT_CHILDREN);
        setNotificationTitleAndText(summary, newMessageInB);

        // messages hold the latest messages, don't show the reply button if user can't see any
        if (connected && messages.size() > 0) builder.addAction(getAction(context, fullName));

        MessagingStyle style = new MessagingStyle(MYSELF);

        // this is ugly on android p, but i see no other way to show the number of messages
        style.setConversationTitle(hotCount < 2 ? shortName : shortName + " (" + String.valueOf(hotCount) + ")");

        // before pie, display private buffers as non-private
        style.setGroupConversation(!hotBuffer.isPrivate || Build.VERSION.SDK_INT < 28);

        addMissingMessageLine(hotCount - messages.size(), res, style, hotBuffer);
        for (HotMessage message : messages) style.addMessage(new MessagingStyle.Message(
                message.message, message.timestamp, getNickForBuffer(message)
        ));
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

    // WARNING hotBuffer shouldn't be null on android p!
    // we have to display *something* (not a space) in place of the user name on android p, since
    // the big icon is generated from it. this is ugly but ¯\_(ツ)_/¯
    private static void addMissingMessageLine(int missingMessages, Resources res, MessagingStyle style, @Nullable Hotlist.HotBuffer hotBuffer) {
        if (missingMessages == 0) return;
        CharSequence nick = ZERO_WIDTH_SPACE;
        if (Build.VERSION.SDK_INT >= 28)
            nick = hotBuffer != null && hotBuffer.isPrivate ?
                    hotBuffer.shortName :
                    res.getQuantityString(R.plurals.hot_messages_missing_user, missingMessages);
        String message = res.getQuantityString(R.plurals.hot_messages_missing, missingMessages, missingMessages);
        style.addMessage(message, 0, nick);
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
        return new NotificationCompat.Action.Builder(R.drawable.ic_toolbar_send,
                replyLabel, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // for android p, use the regular nick, as messages from the same user are displayed without
    // repeating the name. on previous versions of android, use a fake name; the real name (short
    // buffer name, actually) will be displayed as conversation title
    // it is possible to have “nick (3)” as a nickname, but icon shade depends on the nickname, and
    // you can't get away with making a Person's with different names but the same key
    private static CharSequence getNickForBuffer(HotMessage message) {
        if (Build.VERSION.SDK_INT >= 28) return message.nick;           // + " (" + hotCount + ")";
        if (message.hotBuffer.isPrivate || message.isAction) return ZERO_WIDTH_SPACE;
        return message.nick;
    }

    private static CharSequence getNickForFullList(HotMessage message) {
        if (message.isAction && message.hotBuffer.isPrivate) return ZERO_WIDTH_SPACE;
        StringBuilder text = new StringBuilder(message.hotBuffer.shortName).append(":");
        if (!message.isAction && !message.hotBuffer.isPrivate) text.append(" ").append(message.nick);
        return text.toString();
    }

    // don't display 2 lines of text on platforms that handle a single line fine
    private static void setNotificationTitleAndText(Builder builder, CharSequence text) {
        if (Build.VERSION.SDK_INT > 23) {
            builder.setContentTitle(text);
        } else {
            builder.setContentTitle(context.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                    .setContentText(text);
        }
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
