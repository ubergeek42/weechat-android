// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.RemoteInput;
import android.text.SpannableString;
import android.text.style.StyleSpan;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.Notificator;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static android.text.TextUtils.isEmpty;
import static com.ubergeek42.WeechatAndroid.relay.Buffer.PRIVATE;
import static com.ubergeek42.WeechatAndroid.service.Notificator.KEY_TEXT_REPLY;


public class Hotlist {
    final private static @Root Kitty kitty = Kitty.make();

    @SuppressLint("UseSparseArrays")
    final private static HashMap<Long, HotBuffer> hotList = new HashMap<>();
    private static AtomicInteger totalHotCount = new AtomicInteger(0);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// classes
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class HotBuffer {
        public final boolean isPrivate;
        public final long pointer;
        public String shortName;
        public final ArrayList<HotMessage> messages = new ArrayList<>();
        public int hotCount = 0;
        public long lastMessageTimestamp = System.currentTimeMillis();

        HotBuffer(Buffer buffer) {
            isPrivate = buffer.type == PRIVATE;
            pointer = buffer.pointer;
            shortName = buffer.shortName;
        }

        // if hot count has changed — either:
        //  * the buffer was closed or read (hot count == 0)
        //  * (hotlist) brought us new numbers (See Buffer.updateHotlist()). that would happen if:
        //      * the buffer was read in weechat (new number is lower or higher (only if not
        //        syncing). if we kept syncing (invalidateMessages == false), new number is going to
        //        be lower and the messages will still be valid, so we can simply truncate them. if
        //        we weren't syncing, invalidateMessages will be true.
        //      * the buffer wasn't read in weechat, and the new number is higher. while the
        //        messages are valid, the list now looks something like this:
        //            ? ? ? <message> <message> ?
        //        instead of displaying it like this, let's clear it for the sake of simplicity
        void updateHotCount(Buffer buffer, boolean invalidateMessages) {
            int newHotCount = buffer.getHotCount();
            boolean updatingHotCount = hotCount != newHotCount;
            boolean updatingShortName = !shortName.equals(buffer.shortName);
            if (!updatingHotCount && !updatingShortName && !(invalidateMessages && messages.size() > 0)) return;
            kitty.info("updateHotCount(%s): %s -> %s (invalidate=%s)", buffer, hotCount, newHotCount, invalidateMessages);
            if (invalidateMessages) {
                messages.clear();
            } else if (updatingHotCount) {
                if (newHotCount > hotCount) {
                    messages.clear();
                } else {
                    int toRemove = messages.size() - newHotCount;
                    if (toRemove >= 0) messages.subList(0, toRemove).clear();
                }
            }
            if (updatingHotCount) setHotCount(newHotCount);
            if (updatingShortName) shortName = buffer.shortName;
            notifyHotlistChanged(this, NotifyReason.HOT_ASYNC);
        }

        void onNewHotLine(Buffer buffer, Line line) {
            setHotCount(hotCount + 1);
            shortName = buffer.shortName;
            HotMessage message = new HotMessage(line, this);
            messages.add(message);
            notifyHotlistChanged(this, NotifyReason.HOT_SYNC);

            if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.NOTIFICATION) && Engine.isEnabledForLine(line)) {
                ContentUriFetcher.loadFirstUrlFromText(message.message, (Uri imageUri) -> {
                    message.image = imageUri;
                    notifyHotlistChanged(this, NotifyReason.HOT_ASYNC);
                });
            }
        }

        void clear() {
            if (hotCount == 0) return;
            setHotCount(0);
            messages.clear();
            notifyHotlistChanged(this, NotifyReason.HOT_ASYNC);
        }

        private void setHotCount(int newHotCount) {
            lastMessageTimestamp = System.currentTimeMillis();
            totalHotCount.addAndGet(newHotCount - hotCount);
            hotCount = newHotCount;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class HotMessage {
        public CharSequence message;
        public final CharSequence nick;
        public final long timestamp;
        public final HotBuffer hotBuffer;
        public final boolean isAction;
        public Uri image = null;

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
        for (HotBuffer buffer : hotList.values()) notifyHotlistChanged(buffer, NotifyReason.REDRAW);
    }

    @Cat synchronized static void onNewHotLine(@NonNull Buffer buffer, @NonNull Line line) {
        getHotBuffer(buffer).onNewHotLine(buffer, line);
    }

    synchronized static void adjustHotListForBuffer(final @NonNull Buffer buffer, boolean invalidateMessages) {
        getHotBuffer(buffer).updateHotCount(buffer, invalidateMessages);
    }

    @Cat synchronized static void makeSureHotlistDoesNotContainInvalidBuffers() {
        for(Iterator<Map.Entry<Long, HotBuffer>> it = hotList.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long, HotBuffer> entry = it.next();
            if (BufferList.findByPointer(entry.getKey()) == null) {
                entry.getValue().clear();
                it.remove();
            }
        }
    }

    public static int getHotCount() {
        return totalHotCount.get();
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

    public enum NotifyReason {HOT_SYNC, HOT_ASYNC, REDRAW}
    private static void notifyHotlistChanged(HotBuffer buffer, NotifyReason reason) {
        ArrayList<HotMessage> allMessages = new ArrayList<>();
        int hotBufferCount = 0;
        long lastMessageTimestamp = 0;

        synchronized (Hotlist.class) {
            for (HotBuffer b : hotList.values()) {
                if (b.hotCount == 0) continue;
                hotBufferCount++;
                allMessages.addAll(b.messages);
                if (b.lastMessageTimestamp > lastMessageTimestamp)
                    lastMessageTimestamp = b.lastMessageTimestamp;
            }
        }

        // older messages come first
        Collections.sort(allMessages, (m1, m2) -> (int) (m1.timestamp - m2.timestamp));
        Notificator.showHot(connected, totalHotCount.get(), hotBufferCount, allMessages, buffer, reason, lastMessageTimestamp);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////// remote input receiver
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class InlineReplyReceiver extends BroadcastReceiver {
        @MainThread @Override public void onReceive(Context context, Intent intent) {
            final String strPointer = intent.getAction();
            final long pointer = Utils.pointerFromString(intent.getAction());
            final CharSequence input = getMessageText(intent);
            final Buffer buffer = BufferList.findByPointer(pointer);
            if (isEmpty(input) || buffer == null || !connected) {
                kitty.error("error while receiving remote input: pointer=%s, input=%s, " +
                        "buffer=%s, connected=%s", strPointer, input, buffer, connected);
                Weechat.showShortToast("Something went terribly wrong");
                return;
            }
            //noinspection ConstantConditions   -- linter error
            Events.SendMessageEvent.fireInput(buffer.fullName, input.toString());
            buffer.flagResetHotMessagesOnNewOwnLine = true;
        }
    }

    private static @Nullable CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) return remoteInput.getCharSequence(KEY_TEXT_REPLY);
        return null;
    }
}
