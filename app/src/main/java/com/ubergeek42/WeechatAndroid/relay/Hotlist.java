// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;
import android.text.SpannableString;
import android.text.style.StyleSpan;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.Notificator;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import static android.support.v4.app.NotificationCompat.MessagingStyle.Message;
import static android.text.TextUtils.isEmpty;
import static com.ubergeek42.WeechatAndroid.relay.Buffer.PRIVATE;
import static com.ubergeek42.WeechatAndroid.service.Notificator.KEY_TEXT_REPLY;


public class Hotlist {
    final private static @Root Kitty kitty = Kitty.make();

    @SuppressLint("UseSparseArrays")
    final private static HashMap<Long, HotBuffer> hotList = new HashMap<>();
    private static volatile int totalHotCount = 0;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// classes
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class HotBuffer {
        final boolean isPrivate;
        public final String fullName;
        public String shortName;
        public final ArrayList<HotMessage> messages = new ArrayList<>();
        public int hotCount = 0;

        HotBuffer(Buffer buffer) {
            isPrivate = buffer.type == PRIVATE;
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
            notifyHotlistChanged(this, false);
        }

        void onNewHotLine(Buffer buffer, Line line) {
            setHotCount(hotCount + 1);
            shortName = buffer.shortName;
            messages.add(new HotMessage(line, this));
            notifyHotlistChanged(this, true);
        }

        private void setHotCount(int newHotCount) {
            totalHotCount += newHotCount - hotCount;
            hotCount = newHotCount;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class HotMessage {
        private CharSequence message;
        private final CharSequence nick;
        private final long timestamp;
        private final HotBuffer hotBuffer;
        private final boolean isAction;

        HotMessage(Line line, HotBuffer hotBuffer) {
            this.hotBuffer = hotBuffer;
            this.isAction = line.action;
            message = Color.stripEverything(line.message);
            nick = (line.speakingNick != null ? line.speakingNick : Color.stripEverything(line.prefix));
            timestamp = line.date.getTime();
            if (line.action) {
                SpannableString sb = new SpannableString(message);
                sb.setSpan(new StyleSpan(Typeface.ITALIC), 0, message.length(), 0);
                message = sb;
            }
        }

        // never show buffer name, show nickname only if relevant
        public Message forBuffer() {
            return new Message(message, timestamp, hotBuffer.isPrivate || isAction ? "" : nick);
        }

        // always show buffer name, show nickname only if relevant
        public Message forFullList() {
            StringBuilder n = new StringBuilder(hotBuffer.shortName).append(":");
            if (!isAction && !hotBuffer.isPrivate) n.append(" ").append(nick);
            return new Message(message, timestamp, n.toString());
        }

        // show the weechat-style message
        public CharSequence forTicker() {
            StringBuilder n = new StringBuilder();
            if (!hotBuffer.isPrivate) n.append(hotBuffer.shortName).append(" ");
            if (isAction) return n.append("*").append(message);
            return n.append("<").append(nick).append("> ").append(message);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// public methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only purpose of this is to show/hide the action button when connecting/disconnecting
    private static boolean connected = false;
    @Cat synchronized public static void redraw(boolean connected) {
        if (Hotlist.connected == connected) return;
        Hotlist.connected = connected;
        for (HotBuffer buffer : hotList.values()) notifyHotlistChanged(buffer, false);
    }

    @Cat synchronized static void onNewHotLine(@NonNull Buffer buffer, @NonNull Line line) {
        getHotBuffer(buffer).onNewHotLine(buffer, line);
    }

    @Cat synchronized static void adjustHotListForBuffer(final @NonNull Buffer buffer) {
        getHotBuffer(buffer).updateHotCount(buffer);
    }

    public static int getHotCount() {
        return totalHotCount;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // creates hot buffer if not present. note that buffers that lose hot messages aren't removed!
    private static @NonNull HotBuffer getHotBuffer(Buffer buffer) {
        HotBuffer hotBuffer = hotList.get(buffer.pointer);
        if (hotBuffer == null) {
            hotBuffer = new HotBuffer(buffer);
            hotList.put(buffer.pointer, hotBuffer);
        }
        return hotBuffer;
    }

    private static void notifyHotlistChanged(HotBuffer buffer, boolean newHighlight) {
        ArrayList<HotMessage> allMessages = new ArrayList<>();
        int hotBufferCount = 0;

        for (HotBuffer b : hotList.values()) {
            if (b.hotCount == 0) continue;
            hotBufferCount++;
            allMessages.addAll(b.messages);
        }

        // older messages come first
        Collections.sort(allMessages, (m1, m2) -> (int) (m1.timestamp - m2.timestamp));
        Notificator.showHot(connected, totalHotCount, hotBufferCount, allMessages, buffer, newHighlight);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////// remote input receiver
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class InlineReplyReceiver extends BroadcastReceiver {
        @MainThread @Override public void onReceive(Context context, Intent intent) {
            final String fullName = intent.getAction();
            final CharSequence input = getMessageText(intent);
            final Buffer buffer = BufferList.findByFullName(fullName);
            if (isEmpty(fullName) || isEmpty(input) || buffer == null || !connected) {
                kitty.error("error while receiving remote input: fullName=%s, input=%s, " +
                        "buffer=%s, connected=%s", fullName, input, buffer, connected);
                Weechat.showShortToast("Something went terribly wrong");
                return;
            }
            Events.SendMessageEvent.fireInput(fullName, input.toString());
            buffer.flagResetHotMessagesOnNewOwnLine = true;
        }
    }

    private static @Nullable CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) return remoteInput.getCharSequence(KEY_TEXT_REPLY);
        return null;
    }
}
