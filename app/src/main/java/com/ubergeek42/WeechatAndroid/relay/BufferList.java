// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.LongSparseArray;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
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

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent;


public class BufferList {
    final private static @Root Kitty kitty = Kitty.make();

    public static volatile @Nullable RelayService relay;
    private static volatile @Nullable BufferListEye buffersEye;

    public static @NonNull ArrayList<Buffer> buffers = new ArrayList<>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread public static void launch(final RelayService relay) {
        Assert.assertNull(BufferList.relay);
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
        RelayMessageHandler h = handlers.put(id, handler);
        Assert.assertNull(h);
    }

    @AnyThread private static void removeMessageHandler(String id, RelayMessageHandler handler) {
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

    @AnyThread synchronized static public void sortFullNames(@NonNull List<String> fullNamesList) {
        final HashMap<String,Integer> bufferNumbers = new HashMap<String,Integer>();

        for (Buffer buffer : buffers) bufferNumbers.put(buffer.fullName, buffer.number);

        Comparator<String> sortByNumberComparator = new Comparator<String>() {
            @Override public int compare(String left, String right) {
                Integer l = bufferNumbers.get(left);
                Integer r = bufferNumbers.get(right);
                if (l == null) l = Integer.MAX_VALUE;
                if (r == null) r = Integer.MAX_VALUE;
                return l.compareTo(r);
            }
        };

        Collections.sort(fullNamesList, sortByNumberComparator);
    }

    @AnyThread static public void setBufferListEye(@Nullable BufferListEye buffersEye) {
        BufferList.buffersEye = buffersEye;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // returns a "random" hot buffer or null
    @MainThread synchronized static public @Nullable Buffer getHotBuffer() {
        for (Buffer buffer : buffers) if (buffer.getHotCount() > 0) return buffer;
        return null;
    }

    @MainThread synchronized static public int getHotBufferCount() {
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
    @MainThread synchronized public static void onGlobalPreferencesChanged(boolean numberChanged) {
        for (Buffer buffer : buffers)
            if (buffer.isOpen) buffer.onGlobalPreferencesChanged(numberChanged);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // if optimizing traffic, sync hotlist to make sure the number of unread messages is correct
    @AnyThread static void syncBuffer(Buffer buffer) {
        if (!P.optimizeTraffic) return;
        SendMessageEvent.fire("sync 0x%x", buffer.pointer);
        syncHotlist();
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

            if (id.equals("listbuffers")) synchronized (BufferList.class) {buffers.clear();}

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
                            ((r = entry.getItem("hidden")) != null) && r.asInt() != 0);
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
                        } else if (Utils.isAnyOf(id, "_buffer_hidden", "_buffer_unhidden")) {
                            buffer.hidden = !id.endsWith("unhidden");
                            notifyBuffersChanged();
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

            synchronized (BufferList.class) {
                for (Buffer buffer : buffers) {
                    Array count = bufferToHotlist.get(buffer.pointer);
                    int others = count == null ? 0 : count.get(0).asInt();
                    int unreads = count == null ? 0 : count.get(1).asInt() + count.get(2).asInt();   // chat messages & private messages
                    int highlights = count == null ? 0 : count.get(3).asInt();                       // highlights
                    buffer.updateHotList(highlights, unreads, others);
                    Hotlist.adjustHotListForBuffer(buffer);
                }
            }

            //processHotCountAndAdjustNotification(false);
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

            for (int i = 0, size = data.getCount(); i < size; i++)
                buffer.addLine(getLine(data.getItem(i)), isBottom);

            if (!isBottom) {
                buffer.onLinesListed();
                removeMessageHandler(this.id, this);
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
                        String name = entry.getItem("name").asString();
                        boolean away = entry.getItem("color").asString().contains("weechat.color.nicklist_away");
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
