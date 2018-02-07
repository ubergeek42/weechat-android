// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.LongSparseArray;

import com.ubergeek42.WeechatAndroid.service.Notificator;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ListIterator;

import static com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent;


public class BufferList {
    final private static @Root Kitty kitty = Kitty.make();

    private static @Nullable RelayService relay;
    private static @Nullable BufferListEye buffersEye;

    public static @NonNull ArrayList<Buffer> buffers = new ArrayList<>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread public static synchronized void launch(final RelayService relay) {
        BufferList.relay = relay;

        // handle buffer list changes
        // including initial hotlist
        addMessageHandler("listbuffers", bufferListWatcher);

        addMessageHandler("_buffer_opened", bufferListWatcher);
        addMessageHandler("_buffer_renamed", bufferListWatcher);
        addMessageHandler("_buffer_title_changed", bufferListWatcher);
        addMessageHandler("_buffer_localvar_added", bufferListWatcher);
        addMessageHandler("_buffer_localvar_changed", bufferListWatcher);
        addMessageHandler("_buffer_localvar_removed", bufferListWatcher);
        addMessageHandler("_buffer_closing", bufferListWatcher);
        addMessageHandler("_buffer_moved", bufferListWatcher);
        addMessageHandler("_buffer_merged", bufferListWatcher);

        addMessageHandler("hotlist", hotlistInitWatcher);
        addMessageHandler("last_read_lines", lastReadLinesWatcher);

        // handle newly arriving chat lines
        // and chatlines we are reading in reverse
        addMessageHandler("_buffer_line_added", newLineWatcher);

        // handle nicklist init and changes
        addMessageHandler("nicklist", nickListWatcher);
        addMessageHandler("_nicklist", nickListWatcher);
        addMessageHandler("_nicklist_diff", nickListWatcher);

        // request a list of buffers current open, along with some information about them
        SendMessageEvent.fire("(listbuffers) hdata buffer:gui_buffers(*) " +
                "number,full_name,short_name,type,title,nicklist,local_variables,notify");
        syncHotlist();
        SendMessageEvent.fire(P.optimizeTraffic ? "sync * buffers,upgrade" : "sync");
    }

    @WorkerThread public static synchronized void stop() {
        relay = null;
        messageHandlersMap.clear();
    }

    final private static HashMap<String, LinkedHashSet<RelayMessageHandler>> messageHandlersMap = new HashMap<>();

    @AnyThread private static synchronized void addMessageHandler(String id, RelayMessageHandler handler) {
        LinkedHashSet<RelayMessageHandler> handlers = messageHandlersMap.get(id);
        if (handlers == null) messageHandlersMap.put(id, handlers = new LinkedHashSet<>());
        handlers.add(handler);
    }

    @AnyThread private static synchronized void removeMessageHandler(String id, RelayMessageHandler handler) {
        LinkedHashSet<RelayMessageHandler> handlers = messageHandlersMap.get(id);
        if (handlers != null) handlers.remove(handler);
    }

    @WorkerThread public static synchronized void handleMessage(@Nullable RelayObject obj, String id) {
        HashSet<RelayMessageHandler> handlers = messageHandlersMap.get(id);
        if (handlers == null) return;
        for (RelayMessageHandler handler : handlers) handler.handleMessage(obj, id);
    }

    // send synchronization data to weechat and return true. if not connected, return false
    @AnyThread public static synchronized boolean syncHotlist() {
        if (relay == null || !relay.state.contains(STATE.AUTHENTICATED))
            return false;
        SendMessageEvent.fire("(last_read_lines) hdata buffer:gui_buffers(*)/own_lines/last_read_line/data buffer");
        SendMessageEvent.fire("(hotlist) hdata hotlist:gui_hotlist(*) buffer,count");
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @MainThread static public boolean hasData() {
        return buffers.size() > 0;
    }

    @MainThread synchronized static public @Nullable Buffer findByFullName(@Nullable String fullName) {
        if (fullName == null) return null;
        for (Buffer buffer : buffers) if (buffer.fullName.equals(fullName)) return buffer;
        return null;
    }

    @AnyThread synchronized static public void setBufferListEye(@Nullable BufferListEye buffersEye) {
        BufferList.buffersEye = buffersEye;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // returns a "random" hot buffer or null
    @MainThread synchronized static public @Nullable Buffer getHotBuffer() {
        for (Buffer buffer : buffers)
            if (buffer.isHot())
                return buffer;
        return null;
    }

    @MainThread synchronized static public int getHotBufferCount() {
        int count = 0;
        for (Buffer buffer : buffers)
            if (buffer.isHot())
                count++;
        return count;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// called on the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////    from this and Buffer (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////    (also alert Buffer)

    @AnyThread synchronized static void notifyBuffersChanged() {
        if (buffersEye != null) buffersEye.onBuffersChanged();
    }

    // called when no buffers has been added or removed, but
    // buffer changes are such that we should reorder the buffer list
    @WorkerThread synchronized static private void notifyBufferPropertiesChanged(Buffer buffer) {
        buffer.onPropertiesChanged();
        if (buffersEye != null) buffersEye.onBuffersChanged();
    }

    // process all open buffers and, if specified, notify them of the change
    @MainThread synchronized public static void onGlobalPreferencesChanged(boolean numberChanged) {
        for (Buffer buffer : buffers)
            if (buffer.isOpen) {
                buffer.lines.processAllMessages(!numberChanged); // todo thread-unsafe!
                buffer.onGlobalPreferencesChanged(numberChanged);
            }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // if optimizing traffic, sync hotlist to make sure the number of unread messages is correct
    @AnyThread synchronized static void syncBuffer(Buffer buffer) {
        if (!P.optimizeTraffic) return;
        SendMessageEvent.fire("sync 0x%x", buffer.pointer);
        syncHotlist();
    }

    @AnyThread synchronized static void desyncBuffer(Buffer buffer) {
        if (P.optimizeTraffic) SendMessageEvent.fire("desync 0x%x", buffer.pointer);
    }

    private static int counter = 0;
    private final static String MEOW = "(%d) hdata buffer:0x%x/own_lines/last_line(-%d)/data date,displayed,prefix,message,highlight,notify,tags_array";
    @MainThread static void requestLinesForBufferByPointer(long pointer, int number) {
        addMessageHandler(Integer.toString(counter), new BufferLineWatcher(counter, pointer));
        SendMessageEvent.fire(MEOW, counter, pointer, number);
        counter++;
    }

    @MainThread static void requestNicklistForBufferByPointer(long pointer) {
        SendMessageEvent.fire("(nicklist) nicklist 0x%x", pointer);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // a list of most recent ACTUALLY RECEIVED hot messages
    // each entry has a form of {"irc.free.#123", "<nick> hi there"}
    static public ArrayList<String[]> hotList = new ArrayList<>();

    // this is the value calculated from hotlist received from weechat
    // it MIGHT BE GREATER than size of hotList
    // initialized, it's -1, so that on service restart we know to remove notification 43
    static private int hotCount = -1;

    // returns hot count or 0 if unknown
    @AnyThread synchronized static public int getHotCount() {
        return hotCount == -1 ? 0 : hotCount;
    }

    // called when a new new hot message just arrived
    @WorkerThread synchronized static void newHotLine(final @NonNull Buffer buffer, final @NonNull Line line) {
        hotList.add(new String[]{buffer.fullName, line.getNotificationString()});
        processHotCountAndNotifyIfChanged(true);
    }

    // called when buffer is read or closed
    // must be called AFTER buffer hot count adjustments / buffer removal from the list
    @AnyThread synchronized static void removeHotMessagesForBuffer(final @NonNull Buffer buffer) {
        for (Iterator<String[]> it = hotList.iterator(); it.hasNext(); )
            if (it.next()[0].equals(buffer.fullName)) it.remove();
        processHotCountAndNotifyIfChanged(false);
    }

    // remove a number of messages for a given buffer, leaving last 'leave' messages
    // DOES NOT notify anyone of the change
    @WorkerThread synchronized static void adjustHotMessagesForBuffer(final @NonNull Buffer buffer, int leave) {
        for (ListIterator<String[]> it = hotList.listIterator(hotList.size()); it.hasPrevious();) {
            if (it.previous()[0].equals(buffer.fullName) && (leave-- <= 0))
                it.remove();
        }
    }

    @WorkerThread private synchronized static void onHotlistFinished() {
        processHotCountAndNotifyIfChanged(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // stores hotCount; returns true if hot count has changed
    @AnyThread static private void processHotCountAndNotifyIfChanged(boolean newHighlight) {
        int hot = 0;
        for (Buffer buffer : buffers) {
            hot += buffer.highlights;
            if (buffer.type == Buffer.PRIVATE) hot += buffer.unreads;
        }
        if (hotCount == hot) return;
        hotCount = hot;
        Notificator.showHot(newHighlight);
        if (buffersEye != null) buffersEye.onHotCountChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread synchronized static private @Nullable Buffer findByPointer(long pointer) {
        for (Buffer buffer : buffers) if (buffer.pointer == pointer) return buffer;
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// yay!! message handlers!! the joy
    //////////////////////////////////////////////////////////////////////////////////////////////// buffer list
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static RelayMessageHandler bufferListWatcher = new RelayMessageHandler() {
        final private @Root Kitty kitty = BufferList.kitty.kid("bufferListWatcher");

        @WorkerThread @Override @Cat public void handleMessage(RelayObject obj, String id) {
            Hdata data = (Hdata) obj;

            if (id.equals("listbuffers")) buffers.clear();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                if (id.equals("listbuffers") || id.equals("_buffer_opened")) {
                    RelayObject r;
                    Buffer buffer = new Buffer(entry.getPointerLong(),
                            entry.getItem("number").asInt(),
                            entry.getItem("full_name").asString(),
                            entry.getItem("short_name").asString(),
                            entry.getItem("title").asString(),
                            ((r = entry.getItem("notify")) != null) ? r.asInt() : 3,
                            (Hashtable) entry.getItem("local_variables"));
                    synchronized (BufferList.class) {buffers.add(buffer);}
                    notifyBuffersChanged();
                } else {
                    Buffer buffer = findByPointer(entry.getPointerLong(0));
                    if (buffer == null) {
                        kitty.warn("handleMessage(..., %s): buffer is not present", id);
                    } else {
                        if (id.equals("_buffer_renamed")) {
                            buffer.fullName = entry.getItem("full_name").asString();
                            String short_name = entry.getItem("short_name").asString();
                            buffer.shortName = (short_name != null) ? short_name : buffer.fullName;
                            buffer.localVars = (Hashtable) entry.getItem("local_variables");
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_title_changed")) {
                            buffer.title = entry.getItem("title").asString();
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.startsWith("_buffer_localvar_")) {
                            buffer.localVars = (Hashtable) entry.getItem("local_variables");
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_moved") || id.equals("_buffer_merged")) {
                            buffer.number = entry.getItem("number").asInt();
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_closing")) {
                            synchronized (BufferList.class) {buffers.remove(buffer);}
                            buffer.onBufferClosed();
                            notifyBuffersChanged();
                        } else {
                            kitty.warn("handleMessage(..., %s): unknown message id", id);
                        }
                    }
                }
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // last_read_lines
    private static RelayMessageHandler lastReadLinesWatcher = new RelayMessageHandler() {
        final private @Root Kitty kitty = BufferList.kitty.kid("lastReadLinesWatcher");

        @WorkerThread @Override @Cat public void handleMessage(RelayObject obj, String id) {
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;

            LongSparseArray<Long> bufferToLrl = new LongSparseArray<>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long bufferPointer = entry.getItem("buffer").asPointerLong();
                long linePointer = entry.getPointerLong();
                bufferToLrl.put(bufferPointer, linePointer);
            }

            synchronized (BufferList.class) {
                for (Buffer buffer : buffers) {
                    Long linePointer = bufferToLrl.get(buffer.pointer);
                    buffer.updateLastReadLine(linePointer == null ? -1 : linePointer);
                }
            }
        }
    };

    // hotlist
    private static RelayMessageHandler hotlistInitWatcher = new RelayMessageHandler() {
        final private @Root Kitty kitty = BufferList.kitty.kid("hotlistInitWatcher");

        @WorkerThread @Override @Cat public void handleMessage(RelayObject obj, String id) {
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;

            LongSparseArray<Array> bufferToHotlist = new LongSparseArray<>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long pointer = entry.getItem("buffer").asPointerLong();
                Array count = entry.getItem("count").asArray();
                bufferToHotlist.put(pointer, count);
            }

            for (Buffer buffer: buffers) {
                Array count = bufferToHotlist.get(buffer.pointer);
                int others = count == null ? 0 : count.get(0).asInt();
                int unreads = count == null ? 0 : count.get(1).asInt() + count.get(2).asInt();   // chat messages & private messages
                int highlights = count == null ? 0 : count.get(3).asInt();                       // highlights
                buffer.updateHighlightsAndUnreads(highlights, unreads, others);
            }

            onHotlistFinished();
            notifyBuffersChanged();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // _buffer_line_added
    // (digit)
    static class BufferLineWatcher implements RelayMessageHandler {
        final private @Root Kitty kitty = BufferList.kitty.kid("BufferLineWatcher");
        final private long bufferPointer;
        final private int id;

        BufferLineWatcher(int id, long bufferPointer) {
            this.bufferPointer = bufferPointer;
            this.id = id;
        }

        @WorkerThread @Override @Cat public void handleMessage(RelayObject obj, String id) {
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            boolean isBottom = id.equals("_buffer_line_added");

            Buffer buffer = findByPointer(isBottom ? data.getItem(0).getItem("buffer").asPointerLong() : bufferPointer);
            if (buffer == null) {
                kitty.warn("handleMessage(..., %s): no buffer to update", id);
                return;
            }

            for (int i = 0, size = data.getCount(); i < size; i++)
                buffer.addLine(getLine(data.getItem(i)), isBottom);

            if (!isBottom) {
                buffer.onLinesListed();
                removeMessageHandler(Integer.toString(this.id), this);
            }
        }

        private static Line getLine(HdataEntry entry) {
            String message = entry.getItem("message").asString();
            String prefix = entry.getItem("prefix").asString();
            boolean displayed = (entry.getItem("displayed").asChar() == 0x01);
            Date time = entry.getItem("date").asTime();
            RelayObject high = entry.getItem("highlight");
            boolean highlight = (high != null && high.asChar() == 0x01);
            RelayObject tagsobj = entry.getItem("tags_array");
            String[] tags = (tagsobj != null && tagsobj.getType() == RelayObject.WType.ARR) ?
                    tagsobj.asArray().asStringArray() : null;
            return new Line(entry.getPointerLong(), time, prefix, message, displayed, highlight, tags);
        }
    }

    private static BufferLineWatcher newLineWatcher = new BufferLineWatcher(-1, -1);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// nicklist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final char ADD = '+';
    private static final char REMOVE = '-';
    private static final char UPDATE = '*';

    // the following two are rather the same thing. so check if it's not _diff
    // nicklist
    // _nicklist
    // _nicklist_diff
    private static RelayMessageHandler nickListWatcher = new RelayMessageHandler() {
        final private @Root Kitty kitty = BufferList.kitty.kid("nickListWatcher");

        @WorkerThread @Override @Cat public void handleMessage(RelayObject obj, String id) {
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            boolean diff = id.equals("_nicklist_diff");
            HashSet<Buffer> renickedBuffers = new HashSet<>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                // find buffer
                Buffer buffer = findByPointer(entry.getPointerLong(0));
                if (buffer == null) {
                    kitty.warn("handleMessage(..., %s): no buffer to update", id);
                    continue;
                }

                // if buffer doesn't hold all nicknames yet, break execution, since full nicks will be requested anyway later
                // erase nicklist if we have a full list here
                if (diff && !buffer.holdsAllNicks) continue;
                if (!diff && renickedBuffers.add(buffer)) buffer.removeAllNicks();

                // decide whether it's adding, removing or updating nicks
                // if _nicklist, treat as if we have _diff = '+'
                char command = (diff) ? entry.getItem("_diff").asChar() : ADD;

                // do the job, but
                // care only for items that are visible (e.g. not 'root')
                // and that are not grouping items
                if (command == ADD || command == UPDATE) {
                    if (entry.getItem("visible").asChar() != 0 && entry.getItem("group").asChar() != 1) {
                        long pointer = entry.getPointerLong();
                        String prefix = entry.getItem("prefix").asString();
                        String name = entry.getItem("name").asString();
                        boolean away = entry.getItem("color").asString().contains("weechat.color.nicklist_away");
                        if (command == ADD)
                            buffer.addNick(pointer, prefix, name, away);
                        else
                            buffer.updateNick(pointer, prefix, name, away);
                    }
                } else if (command == REMOVE) {
                    buffer.removeNick(entry.getPointerLong());
                }
            }

            // sort nicknames when we receive them for the very first time
            if (id.equals("nicklist"))
                for (Buffer buffer : renickedBuffers) {
                    buffer.holdsAllNicks = true;
                    buffer.sortNicksByLines();
                }
        }
    };
}
