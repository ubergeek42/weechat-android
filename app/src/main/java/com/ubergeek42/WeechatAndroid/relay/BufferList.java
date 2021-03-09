// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.util.LongSparseArray;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
import com.ubergeek42.WeechatAndroid.utils.Assert;
import com.ubergeek42.WeechatAndroid.utils.Utils;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent;
import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;


public class BufferList {
    final private static @Root Kitty kitty = Kitty.make();

    public static volatile @Nullable RelayService relay;
    private static volatile @Nullable BufferListEye buffersEye;

    final public static @NonNull CopyOnWriteArrayList<Buffer> buffers = new CopyOnWriteArrayList<>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread public static void launch(final RelayService relay) {
        assertThat(BufferList.relay).isNull();
        BufferList.relay = relay;

        // handle buffer list changes
        // including initial hotlist
        addMessageHandler("listbuffers", bufferListWatcher);
        addMessageHandler("renumber", bufferListWatcher);

        addMessageHandler("_buffer_opened", bufferListWatcher);
        addMessageHandler("_buffer_renamed", bufferListWatcher);
        addMessageHandler("_buffer_title_changed", bufferListWatcher);
        addMessageHandler("_buffer_localvar_added", bufferListWatcher);
        addMessageHandler("_buffer_localvar_changed", bufferListWatcher);
        addMessageHandler("_buffer_localvar_removed", bufferListWatcher);
        addMessageHandler("_buffer_closing", bufferListWatcher);
        addMessageHandler("_buffer_moved", bufferListWatcher);
        addMessageHandler("_buffer_merged", bufferListWatcher);
        addMessageHandler("_buffer_hidden", bufferListWatcher);
        addMessageHandler("_buffer_unhidden", bufferListWatcher);

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
                "number,full_name,short_name,type,title,nicklist,local_variables,notify,hidden");
        syncHotlist();
        SendMessageEvent.fire(P.optimizeTraffic ? "sync * buffers,upgrade" : "sync");
    }

    @AnyThread public static void stop() {
        relay = null;
        handlers.clear();
    }

    final private static ConcurrentHashMap<String, RelayMessageHandler> handlers = new ConcurrentHashMap<>();

    @AnyThread private static void addMessageHandler(String id, RelayMessageHandler handler) {
        assertThat(handlers.put(id, handler)).isNull();
    }

    @AnyThread public static String addOneOffMessageHandler(RelayMessageHandler handler) {
        RelayMessageHandler wrappedHandler = new RelayMessageHandler() {
            @Override public void handleMessage(RelayObject obj, String id) {
                handler.handleMessage(obj, id);
                removeMessageHandler(id, this);
            }
        };
        String id = String.valueOf(counter++);
        assertThat(handlers.put(id, wrappedHandler)).isNull();
        return id;
    }

    @AnyThread public static void removeMessageHandler(String id, RelayMessageHandler handler) {
        handlers.remove(id, handler);
    }

    @WorkerThread public static void handleMessage(@Nullable RelayObject obj, String id) {
        final RelayMessageHandler handler = handlers.get(id);
        if (handler != null) handler.handleMessage(obj, id);
    }

    // send synchronization data to weechat and return true. if not connected, return false
    @AnyThread public static boolean syncHotlist() {
        RelayService relay = BufferList.relay;
        if (relay == null || !relay.state.contains(STATE.AUTHENTICATED))
            return false;
        SendMessageEvent.fire("(last_read_lines) hdata buffer:gui_buffers(*)/own_lines/last_read_line/data buffer");
        SendMessageEvent.fire("(hotlist) hdata hotlist:gui_hotlist(*) buffer,count");
        return true;
    }

    @AnyThread public static void sortOpenBuffersByBuffers(ArrayList<Long> pointers) {
        final LongSparseArray<Integer> bufferToNumber = new LongSparseArray<>();
        for (Buffer b: buffers) bufferToNumber.put(b.pointer, b.number);
        Collections.sort(pointers, (l, r) -> bufferToNumber.get(l, -1) - bufferToNumber.get(r, -1));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @MainThread static public boolean hasData() {
        return buffers.size() > 0;
    }

    @AnyThread static public void setBufferListEye(@Nullable BufferListEye buffersEye) {
        BufferList.buffersEye = buffersEye;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // returns a "random" hot buffer or null
    @MainThread static public @Nullable Buffer getHotBuffer() {
        for (Buffer buffer : buffers) if (buffer.getHotCount() > 0) return buffer;
        return null;
    }

    @MainThread static public int getHotBufferCount() {
        int count = 0;
        for (Buffer buffer : buffers) if (buffer.getHotCount() > 0) count++;
        return count;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// called on the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////    from this and Buffer (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////    (also alert Buffer)

    @AnyThread static void notifyBuffersChanged() {
        final BufferListEye eye = buffersEye;
        if (eye != null) eye.onBuffersChanged();
    }

    // called when no buffers has been added or removed, but
    // buffer changes are such that we should reorder the buffer list
    @WorkerThread static private void notifyBufferPropertiesChanged(Buffer buffer) {
        buffer.onPropertiesChanged();
        notifyBuffersChanged();
    }

    // process all open buffers and, if specified, notify them of the change
    @MainThread public static void onGlobalPreferencesChanged(boolean numberChanged) {
        for (Buffer buffer : buffers)
            if (buffer.isOpen) buffer.onGlobalPreferencesChanged(numberChanged);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // if optimizing traffic, sync hotlist to make sure the number of unread messages is correct
    // syncHotlist parameter is used to avoid synchronizing hotlist several times in a row when
    // restoring open buffers. todo simplify?
    @AnyThread static void syncBuffer(Buffer buffer, boolean syncHotlist) {
        if (!P.optimizeTraffic) return;
        SendMessageEvent.fire("sync 0x%x", buffer.pointer);
        if (syncHotlist) syncHotlist();
    }

    @AnyThread static void desyncBuffer(Buffer buffer) {
        if (!P.optimizeTraffic) return;
        SendMessageEvent.fire("desync 0x%x", buffer.pointer);
    }

    private static int counter = 0;
    private final static String MEOW = "(%d) hdata buffer:0x%x/own_lines/last_line(-%d)/data date,displayed,prefix,message,highlight,notify,tags_array";
    @MainThread static void requestLinesForBufferByPointer(long pointer, int number) {
        String id = String.valueOf(counter);
        addMessageHandler(id, new BufferLineWatcher(id, pointer));
        SendMessageEvent.fire(MEOW, counter, pointer, number);
        counter++;
    }

    @MainThread static void requestNicklistForBufferByPointer(long pointer) {
        SendMessageEvent.fire("(nicklist) nicklist 0x%x", pointer);
    }

    private static void requestRenumber() {
        SendMessageEvent.fire("(renumber) hdata buffer:gui_buffers(*) number");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread public static @Nullable Buffer findByPointer(long pointer) {
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
                            (Hashtable) entry.getItem("local_variables"),
                            ((r = entry.getItem("hidden")) != null) && r.asInt() != 0,
                            id.equals("_buffer_opened"));
                    buffers.add(buffer);
                } else if (id.equals("renumber")) {
                    Buffer buffer = findByPointer(entry.getPointerLong(0));
                    int number = entry.getItem("number").asInt();
                    if (buffer != null && buffer.number != number) {
                        buffer.number = number;
                        buffer.onPropertiesChanged();
                    }
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
                            // buffer.number = entry.getItem("number").asInt();
                            // notifyBufferPropertiesChanged(buffer);
                            requestRenumber();
                        } else if (Utils.isAnyOf(id, "_buffer_hidden", "_buffer_unhidden")) {
                            buffer.hidden = !id.endsWith("unhidden");
                            notifyBuffersChanged();
                        } else if (id.equals("_buffer_closing")) {
                            buffers.remove(buffer);
                            buffer.onBufferClosed();
                            notifyBuffersChanged();
                        } else {
                            kitty.warn("handleMessage(..., %s): unknown message id", id);
                        }
                    }
                }
            }
            if (id.equals("listbuffers") || id.equals("renumber")) {
                notifyBuffersChanged();
                Hotlist.makeSureHotlistDoesNotContainInvalidBuffers();
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

            bufferToLrl.clear();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long bufferPointer = entry.getItem("buffer").asPointerLong();
                long linePointer = entry.getPointerLong();
                bufferToLrl.put(bufferPointer, linePointer);
            }
        }
    };

    static final long LAST_READ_LINE_MISSING = -1;
    static private LongSparseArray<Long> bufferToLrl = new LongSparseArray<>();
    static private long lastHotlistUpdateTime = 0;

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

            long newLastHotlistUpdateTime = System.currentTimeMillis();
            long timeSinceLastHotlistUpdate = newLastHotlistUpdateTime - lastHotlistUpdateTime;
            lastHotlistUpdateTime = newLastHotlistUpdateTime;

            for (Buffer buffer : buffers) {
                Array count = bufferToHotlist.get(buffer.pointer);
                int unreads = count == null ? 0 : count.get(1).asInt() + count.get(2).asInt();   // chat messages & private messages
                int highlights = count == null ? 0 : count.get(3).asInt();                       // highlights

                long linePointer = bufferToLrl.get(buffer.pointer, LAST_READ_LINE_MISSING);
                boolean hotMessagesInvalid = buffer.updateHotlist(highlights, unreads, linePointer, timeSinceLastHotlistUpdate);
                Hotlist.adjustHotListForBuffer(buffer, hotMessagesInvalid);
            }

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
        final private String id;

        BufferLineWatcher(String id, long bufferPointer) {
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

            if (isBottom) {
                Assert.assertThat(data.getCount()).isEqualTo(1);
                Line line = Line.make(data.getItem(0));
                buffer.addLineBottom(line);

                buffer.onLineAdded();
            } else {
                int dataSize = data.getCount();
                ArrayList<Line> lines = new ArrayList<>(data.getCount());
                for (int i = dataSize - 1; i >= 0; i--) { lines.add(Line.make(data.getItem(i))); }
                buffer.replaceLines(lines);

                buffer.onLinesListed();
                removeMessageHandler(this.id, this);
            }
        }
    }

    private static BufferLineWatcher newLineWatcher = new BufferLineWatcher("", -1);

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
                if (diff && !buffer.nicksAreReady()) continue;
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
                        if (" ".equals(prefix)) prefix = "";
                        String name = entry.getItem("name").asString();
                        String color = entry.getItem("color").asString();
                        boolean away = color != null && color.contains("weechat.color.nicklist_away");
                        Nick nick = new Nick(pointer, prefix, name, away);
                        if (command == ADD)
                            buffer.addNick(nick);
                        else
                            buffer.updateNick(nick);
                    }
                } else if (command == REMOVE) {
                    buffer.removeNick(entry.getPointerLong());
                }
            }

            // sort nicknames when we receive them for the very first time
            if (id.equals("nicklist"))
                for (Buffer buffer : renickedBuffers) buffer.onNicksListed();
        }
    };
}
