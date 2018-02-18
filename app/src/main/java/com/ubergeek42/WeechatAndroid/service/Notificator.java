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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;
import java.util.HashMap;

import static android.support.v4.app.NotificationCompat.MessagingStyle.Message;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.AUTHENTICATED;
import static com.ubergeek42.WeechatAndroid.utils.Constants.NOTIFICATION_EXTRA_BUFFER_FULL_NAME;
import static com.ubergeek42.WeechatAndroid.utils.Constants.NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY;


public class Notificator {

    final private static @Root Kitty kitty = Kitty.make();

    final private static int NOTIFICATION_MAIN_ID = 42;
    final private static int NOTIFICATION_HOT_ID = 43;
    final private static String NOTIFICATION_CHANNEL_CONNECTION_STATUS = "connection status";
    final private static String NOTIFICATION_CHANNEL_HOTLIST = "notification";

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static NotificationManager manager;

    @MainThread public static void init(Context c) {
        context = c.getApplicationContext();
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_CONNECTION_STATUS,
                context.getString(R.string.notification_channel_connection_status),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_HOTLIST,
                context.getString(R.string.notification_channel_hotlist),
                NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("UseSparseArrays")
    final private static HashMap<Long, HotBuffer> hotList = new HashMap<>();
    private static int totalHotCount = 0;

    static class HotBuffer {
        final boolean isPrivate;
        final String fullName;
        String shortName;
        final ArrayList<Message> messages = new ArrayList<>();
        int hotCount = 0;

        HotBuffer(Buffer buffer) {
            isPrivate = buffer.type == Buffer.PRIVATE;
            fullName = buffer.fullName;
            shortName = buffer.shortName;
        }

        void updateHotCount(Buffer buffer) {
            int newHotCount = buffer.getHotCount();
            if (hotCount == newHotCount) return;
            shortName = buffer.shortName;
            setHotCount(newHotCount);
            int toRemove = messages.size() - hotCount;
            if (toRemove >= 0) messages.subList(0, toRemove).clear();
            showHot(this, false);
        }

        void onNewHotLine(Buffer buffer, Line line) {
            setHotCount(hotCount + 1);
            messages.add(getMessage(line, isPrivate));
            shortName = buffer.shortName;
            showHot(this, true);
        }

        private void setHotCount(int newHotCount) {
            totalHotCount += newHotCount - hotCount;
            hotCount = newHotCount;
        }
    }

    private static boolean connected = false;
    @Cat synchronized static void redraw(boolean connected) {
        Notificator.connected = connected;
        for (HotBuffer buffer : hotList.values()) showHot(buffer, false);
    }

    @Cat public synchronized static void onNewHotLine(@NonNull Buffer buffer, @NonNull Line line) {
        getHotBuffer(buffer).onNewHotLine(buffer, line);
    }

    @Cat(linger=true) public synchronized static void adjustHotListForBuffer(final @NonNull Buffer buffer) {
        kitty.trace("hot=%s", buffer.getHotCount());
        getHotBuffer(buffer).updateHotCount(buffer);
    }

    private static @NonNull HotBuffer getHotBuffer(Buffer buffer) {
        HotBuffer hotBuffer = hotList.get(buffer.pointer);
        if (hotBuffer == null) {
            hotBuffer = new HotBuffer(buffer);
            hotList.put(buffer.pointer, hotBuffer);
        }
        return hotBuffer;
    }

    public synchronized static int getHotCount() {
        return totalHotCount;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread @Cat static void showMain(@NonNull RelayService relay, @NonNull String content) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        boolean authenticated = relay.state.contains(AUTHENTICATED);
        int icon = authenticated ? R.drawable.ic_connected : R.drawable.ic_disconnected;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_CONNECTION_STATUS);
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
    @AnyThread @Cat static void showHot(HotBuffer b, boolean newHighlight) {
        if (!P.notificationEnable)
            return;

        int msgs = 0, chats = 0;
        for (HotBuffer h : hotList.values()) {
            if (h.hotCount == 0) continue;
            chats++;
            msgs += h.hotCount;
        }

        if (b.hotCount == 0) {
            if (msgs == 0) manager.cancel(232);
            manager.cancel(b.fullName, NOTIFICATION_HOT_ID);
            return;
        }

        Notification n = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_HOTLIST)
                .setContentIntent(getIntentFor(NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY))
                .setContentTitle("Weechat-Android1")
                .setContentText("You have unread messages1")
                .setSmallIcon(R.drawable.ic_hot)
                .setGroup("hello")
                .setGroupSummary(true)
                .setSubText(String.format("%s messages in %s chats", msgs, chats))
                .setContentInfo(String.valueOf(b.hotCount))
                //.setNumber(b.hotCount)
                .build();

        manager.notify(232, n);

        // prepare notification
        // make the ticker the LAST message
        String lastMessage;
        if (b.messages.size() == 0) lastMessage = context.getString(R.string.hot_message_not_available);
        else {
            Message last = b.messages.get(b.messages.size() - 1);
            lastMessage = last.getSender() + "  " + last.getText();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_HOTLIST)
                .setContentIntent(getIntentFor(b.fullName))
                .setSmallIcon(R.drawable.ic_hot)
                .setContentTitle(context.getResources().getQuantityString(R.plurals.hot_messages, b.hotCount, b.hotCount, b.shortName))
                .setContentText("hellooooo world")
                .setContentInfo(String.valueOf(b.hotCount));

        if (connected && b.messages.size() > 0) builder.addAction(getAction(context, b.fullName));

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle("");
        if (b.hotCount > b.messages.size()) {
            int missing = b.hotCount - b.messages.size();
            String s = context.getResources().getQuantityString(R.plurals.hot_messages_missing, missing, missing);
            style.addMessage(s, 0, "");
        }
        for (Message message : b.messages) style.addMessage(message);

        //if (!b.isPrivate) style.setConversationTitle(b.shortName);
        style.setConversationTitle(b.hotCount < 2 ? b.shortName : b.shortName + String.format(" (%s)", b.hotCount));
        builder.setStyle(style);
        builder.setGroup("hello");

        if (newHighlight) {
            builder.setTicker(lastMessage);
            builder.setPriority(Notification.PRIORITY_HIGH);
            if (!TextUtils.isEmpty(P.notificationSound)) builder.setSound(Uri.parse(P.notificationSound));
            int flags = 0;
            if (P.notificationLight) flags |= Notification.DEFAULT_LIGHTS;
            if (P.notificationVibrate) flags |= Notification.DEFAULT_VIBRATE;
            builder.setDefaults(flags);
        }

        manager.notify(b.fullName, NOTIFICATION_HOT_ID, builder.build());
    }

    private static PendingIntent getIntentFor(String fullName) {
        Intent intent = new Intent(context, WeechatActivity.class).putExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME, fullName);
        intent.setAction(fullName);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static final String KEY_TEXT_REPLY = "key_text_reply";
    private static NotificationCompat.Action getAction(Context ctx, String fullName) {
        String replyLabel = ctx.getResources().getString(R.string.reply_label);
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build();
        Intent intent = new Intent(ctx, InlineReplyReceiver.class);
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

    private static Message getMessage(Line line, boolean isPrivate) {
        CharSequence message = Color.stripEverything(line.message);
        String nick = isPrivate || line.action ? "" : line.speakingNick;
        if (nick == null) nick = Color.stripEverything(line.prefix);
        if (line.action) {
            SpannableString sb = new SpannableString(message);
            sb.setSpan(new StyleSpan(Typeface.ITALIC), 0, message.length(), 0);
            message = sb;
        }
        return new Message(message, line.date.getTime(), nick);
    }

    public static class InlineReplyReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String fullName = intent.getAction();
            CharSequence line = getMessageText(intent);
            kitty.warn("channel %s message %s", fullName, line);
            if (line == null) return;
            P.addSentMessage(line.toString());
            if (connected) {
                Events.SendMessageEvent.fire("input %s %s", fullName, line);
                Buffer buffer = BufferList.findByFullName(fullName);
                if (buffer != null) buffer.flagResetHotMessagesOnNewOwnLine = true;
            } else {
                Weechat.showShortToast("You should never see this");
            }
        }
    }

    private static CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_TEXT_REPLY);
        }
        return null;
    }
}
