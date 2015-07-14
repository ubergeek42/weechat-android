package com.ubergeek42.WeechatAndroid.service;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ListIterator;

/** a class that holds information about buffers
 ** probably should be made static */

public class BufferList {
    private static Logger logger = LoggerFactory.getLogger("BufferList");
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_SYNCING = false;
    final private static boolean DEBUG_HANDLERS = false;
    final private static boolean DEBUG_HOT = false;
    final private static boolean DEBUG_SAVE_RESTORE = false;

    /** preferences related to the list of buffers.
     ** actually WRITABLE from outside */
    public static boolean SORT_BUFFERS = false;
    public static boolean SHOW_TITLE = true;
    public static boolean FILTER_NONHUMAN_BUFFERS = false;
    public static boolean OPTIMIZE_TRAFFIC = false;
    public static @Nullable String FILTER_LC = null;
    public static @Nullable String FILTER_UC = null;

    /** contains names of open buffers. this is written to the shared preferences
     ** and restored upon service restart (by the system). also this is used to
     ** reopen buffers in case user pressed "back" */
    static public @NonNull LinkedHashSet<String> syncedBuffersFullNames = new LinkedHashSet<>();   // TODO race condition?

    /** this stores information about last read line (in `desktop` weechat) and according
     ** number of read lines/highlights. this is substracted from highlight counts client
     ** receives from the server */
    static private @NonNull LinkedHashMap<String, BufferHotData> bufferToLastReadLine = new LinkedHashMap<>();

    static RelayService relay;
    private static RelayConnection connection;
    private static BufferListEye buffersEye;

    /** the mother variable. list of current buffers */
    private static ArrayList<Buffer> buffers = new ArrayList<Buffer>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static void launch(final RelayService relay) {
        BufferList.relay = relay;
        BufferList.connection = relay.connection;
        buffers.clear();

        // handle buffer list changes
        // including initial hotlist
        connection.addHandler("listbuffers", bufferListWatcher);

        connection.addHandler("_buffer_opened", bufferListWatcher);
        connection.addHandler("_buffer_renamed", bufferListWatcher);
        connection.addHandler("_buffer_title_changed", bufferListWatcher);
        connection.addHandler("_buffer_localvar_added", bufferListWatcher);
        connection.addHandler("_buffer_localvar_changed", bufferListWatcher);
        connection.addHandler("_buffer_localvar_removed", bufferListWatcher);
        connection.addHandler("_buffer_closing", bufferListWatcher);
        connection.addHandler("_buffer_moved", bufferListWatcher);
        connection.addHandler("_buffer_merged", bufferListWatcher);

        connection.addHandler("hotlist", hotlistInitWatcher);
        connection.addHandler("last_read_lines", lastReadLinesWatcher);

        // handle newly arriving chat lines
        // and chatlines we are reading in reverse
        connection.addHandler("_buffer_line_added", bufferLineWatcher);
        connection.addHandler("listlines_reverse", bufferLineWatcher);

        // handle nicklist init and changes
        connection.addHandler("nicklist", nickListWatcher);
        connection.addHandler("_nicklist", nickListWatcher);
        connection.addHandler("_nicklist_diff", nickListWatcher);

        // request a list of buffers current open, along with some information about them
        connection.sendMsg("listbuffers", "hdata", "buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables,notify");
        syncHotlist();
    }

    /** send synchronization data to weechat and return true. if not connected, return false. */
    public static boolean syncHotlist() {
        if (relay == null || !relay.isConnection(RelayServiceBackbone.CONNECTED))
            return false;
        connection.sendMsg("last_read_lines", "hdata", "buffer:gui_buffers(*)/own_lines/lastReadLine/data buffer");
        connection.sendMsg("hotlist", "hdata", "hotlist:gui_hotlist(*) buffer,count");
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static @Nullable ArrayList<Buffer> sentBuffers = null;

    /** returns an independent copy of the buffer list
     ** MIGHT return the same object (albeit sorted as needed) */
    synchronized static public @NonNull ArrayList<Buffer> getBufferList() {
        if (sentBuffers == null) {
            sentBuffers = new ArrayList<>();
            for (Buffer buffer : buffers) {
                if (buffer.type == Buffer.HARD_HIDDEN) continue;
                if (FILTER_NONHUMAN_BUFFERS && buffer.type == Buffer.OTHER && buffer.highlights == 0 && buffer.unreads == 0) continue;
                if (FILTER_LC != null && FILTER_UC != null && !buffer.fullName.toLowerCase().contains(FILTER_LC) && !buffer.fullName.toUpperCase().contains(FILTER_UC)) continue;
                sentBuffers.add(buffer);
            }
        }
        if (SORT_BUFFERS) Collections.sort(sentBuffers, sortByHotAndMessageCountComparator);
        else Collections.sort(sentBuffers, sortByHotCountAndNumberComparator);
        return sentBuffers;
    }

    synchronized static public void setFilter(String filter) {
        FILTER_LC = (filter.length() == 0) ? null : filter.toLowerCase();
        FILTER_UC = (filter.length() == 0) ? null : filter.toUpperCase();
        sentBuffers = null;
    }

    synchronized static public @Nullable Buffer findByFullName(@Nullable String fullName) {
        if (fullName == null) return null;
        for (Buffer buffer : buffers) if (buffer.fullName.equals(fullName)) return buffer;
        return null;
    }

    /** sets or remove (using null) buffer list change watcher */
    synchronized static public void setBufferListEye(@Nullable BufferListEye buffersEye) {
        BufferList.buffersEye = buffersEye;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** returns a "random" hot buffer or null */
    synchronized static public @Nullable Buffer getHotBuffer() {
        for (Buffer buffer : buffers)
            if ((buffer.type == Buffer.PRIVATE && buffer.unreads > 0) || buffer.highlights > 0)
                return buffer;
        return null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// called on the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////    from this and Buffer (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////    (also alert Buffer)

    /** called when a buffer has been added or removed */
    synchronized static private void notifyBuffersChanged() {
        sentBuffers = null;
        if (buffersEye != null) buffersEye.onBuffersChanged();
    }

    /** called when buffer data has been changed, but the no of buffers is the same
     ** otherMessagesChanged signifies if buffer type is OTHER and message count has changed
     ** used to temporarily display the said buffer if OTHER buffers are filtered */
    synchronized static void notifyBuffersSlightlyChanged(boolean otherMessagesChanged) {
        if (buffersEye != null) {
            if (otherMessagesChanged && FILTER_NONHUMAN_BUFFERS) sentBuffers = null;
            buffersEye.onBuffersChanged();
        }
    }

    synchronized static void notifyBuffersSlightlyChanged() {
        notifyBuffersSlightlyChanged(false);
    }

    /** called when no buffers has been added or removed, but
     ** buffer changes are such that we should reorder the buffer list */
    synchronized static private void notifyBufferPropertiesChanged(Buffer buffer) {
        buffer.onPropertiesChanged();
        sentBuffers = null;
        if (buffersEye != null) buffersEye.onBuffersChanged();
    }

    /** process all open buffers and, if specified, notify them of the change
     ** practically notifying is only needed when pressing volume up/dn keys,
     ** which means we are not in the preferences window and the activity will not
     ** get re-rendered */
    synchronized static void notifyOpenBuffersMustBeProcessed(boolean notify) {
        for (Buffer buffer : buffers)
            if (buffer.isOpen) {
                buffer.forceProcessAllMessages();
                if (notify) buffer.onLinesChanged();
            }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized static boolean isSynced(String fullName) {
        return syncedBuffersFullNames.contains(fullName);
    }

    /** send sync command to relay (if traffic is set to optimized) and
     ** add it to the synced buffers (=open buffers) list */
    synchronized static void syncBuffer(String fullName) {
        if (DEBUG_SYNCING) logger.warn("syncBuffer({})", fullName);
        BufferList.syncedBuffersFullNames.add(fullName);
        if (OPTIMIZE_TRAFFIC) relay.connection.sendMsg("sync " + fullName);
    }

    synchronized static void desyncBuffer(String fullName) {
        if (DEBUG_SYNCING) logger.warn("desyncBuffer({})", fullName);
        BufferList.syncedBuffersFullNames.remove(fullName);
        if (OPTIMIZE_TRAFFIC) relay.connection.sendMsg("desync " + fullName);
    }

    public static void requestLinesForBufferByPointer(long pointer) {
        if (DEBUG_SYNCING) logger.warn("requestLinesForBufferByPointer({})", pointer);
        connection.sendMsg("listlines_reverse", "hdata", String.format(
                "buffer:0x%x/own_lines/last_line(-%d)/data date,displayed,prefix,message,highlight,notify,tags_array",
                pointer, Buffer.MAX_LINES));
    }

    public static void requestNicklistForBufferByPointer(long  pointer) {
        if (DEBUG_SYNCING) logger.warn("requestNicklistForBufferByPointer({})", pointer);
        connection.sendMsg(String.format("(nicklist) nicklist 0x%x", pointer));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** a list of most recent ACTUALLY RECEIVED hot messages
     ** each entry has a form of {"irc.free.#123", "<nick> hi there"} */
    static public ArrayList<String[]> hotList = new ArrayList<>();

    /** this is the value calculated from hotlist received from weechat
     ** it MIGHT BE GREATER than size of hotList
     ** initialized, it's -1, so that on service restart we know to remove notification 43 */
    static private int hotCount = -1;

    /** returns hot count or 0 if unknown */
    static public int getHotCount() {
        return hotCount == -1 ? 0 : hotCount;
    }

    /** called when a new new hot message just arrived */
    synchronized static void newHotLine(final @NonNull Buffer buffer, final @NonNull Buffer.Line line) {
        hotList.add(new String[]{buffer.fullName, line.getNotificationString()});
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(true);
    }

    /** called when buffer is read or closed
     ** must be called AFTER buffer hot count adjustments / buffer removal from the list */
    synchronized static void removeHotMessagesForBuffer(final @NonNull Buffer buffer) {
        for (Iterator<String[]> it = hotList.iterator(); it.hasNext(); )
            if (it.next()[0].equals(buffer.fullName)) it.remove();
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(false);
    }

    /** remove a number of messages for a given buffer, leaving last 'leave' messages
     ** DOES NOT notify anyone of the change */
    synchronized static void adjustHotMessagesForBuffer(final @NonNull Buffer buffer, int leave) {
        for (ListIterator<String[]> it = hotList.listIterator(hotList.size()); it.hasPrevious();) {
            if (it.previous()[0].equals(buffer.fullName) && (leave-- <= 0))
                it.remove();
        }
    }

    synchronized static void onHotlistFinished() {
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** HELPER. stores hotCount;
     ** returns true if hot count has changed */
    static private boolean processHotCountAndTellIfChanged() {
        int hot = 0;
        for (Buffer buffer : buffers) {
            hot += buffer.highlights;
            if (buffer.type == Buffer.PRIVATE) hot += buffer.unreads;
        }
        return hotCount != (hotCount = hot); // har har
    }

    /** HELPER. notifies everyone interested of hotlist changes */
    static private void notifyHotCountChanged(boolean newHighlight) {
        relay.changeHotNotification(newHighlight);
        if (buffersEye != null)
            buffersEye.onHotCountChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized static private @Nullable Buffer findByPointer(long pointer) {
        for (Buffer buffer : buffers) if (buffer.pointer == pointer) return buffer;
        return null;
    }

    static private final Comparator<Buffer> sortByHotCountAndNumberComparator = new Comparator<Buffer>() {
        @Override public int compare(Buffer left, Buffer right) {
            int l, r;
            if ((l = left.highlights) != (r = right.highlights)) return r - l;
            if ((l = left.type == Buffer.PRIVATE ? left.unreads : 0) !=
                    (r = right.type == Buffer.PRIVATE ? right.unreads : 0)) return r - l;
            return left.number - right.number;
        }
    };

    static private final Comparator<Buffer> sortByHotAndMessageCountComparator = new Comparator<Buffer>() {
        @Override
        public int compare(Buffer left, Buffer right) {
            int l, r;
            if ((l = left.highlights) != (r = right.highlights)) return r - l;
            if ((l = left.type == Buffer.PRIVATE ? left.unreads : 0) !=
                    (r = right.type == Buffer.PRIVATE ? right.unreads : 0)) return r - l;
            return right.unreads - left.unreads;
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// yay!! message handlers!! the joy
    //////////////////////////////////////////////////////////////////////////////////////////////// buffer list
    ////////////////////////////////////////////////////////////////////////////////////////////////

    static RelayMessageHandler bufferListWatcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.warn("handleMessage(..., {}) (hdata size = {})", id, ((Hdata) obj).getCount());
            Hdata data = (Hdata) obj;

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                if (id.equals("listbuffers") || id.equals("_buffer_opened")) {
                    RelayObject r;
                    Buffer buffer = new Buffer(entry.getPointerLong(),
                            entry.getItem("number").asInt(),
                            entry.getItem("full_name").asString(),
                            entry.getItem("short_name").asString(),
                            entry.getItem("title").asString(),
                            ((r = entry.getItem("notify")) != null) ? r.asInt() : 1,            // TODO request notify level afterwards???
                            (Hashtable) entry.getItem("local_variables"));                      // TODO because _buffer_opened doesn't provide notify level
                    synchronized (BufferList.class) {buffers.add(buffer);}
                    notifyBuffersChanged();
                } else {
                    Buffer buffer = findByPointer(entry.getPointerLong(0));
                    if (buffer == null) {
                        logger.error("handleMessage(..., {}): buffer is not present!", id);
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
                        } else if (id.equals("_buffer_moved") || id.equals("_buffer_merged")) { // TODO if buffer is moved, reorder others?
                            buffer.number = entry.getItem("number").asInt();                    // TODO this is not our issue; it's going to be resolved in future versions of weechat
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_closing")) {
                            synchronized (BufferList.class) {buffers.remove(buffer);}
                            buffer.onBufferClosed();
                            notifyBuffersChanged();
                        } else {
                            if (DEBUG_HANDLERS) logger.warn("Unknown message ID: '{}'", id);
                        }
                    }
                }
            }

            if (id.equals("listbuffers"))
                relay.onBuffersListed();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // last_read_lines
    static RelayMessageHandler lastReadLinesWatcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.error("lastReadLinesWatcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;

            HashMap<Long, Long> bufferToLrl = new HashMap<>();

            Hdata data = (Hdata) obj;
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

    // hotlist (ONLY)
    static RelayMessageHandler hotlistInitWatcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.error("hotlistInitWatcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;

            HashMap<Long, Array> bufferToHotlist = new HashMap<>();

            Hdata data = (Hdata) obj;
            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long pointer = entry.getItem("buffer").asPointerLong();
                Array count = entry.getItem("count").asArray();
                bufferToHotlist.put(pointer, count);
            }

            for (Buffer buffer: buffers) {
                Array count = bufferToHotlist.get(buffer.pointer);
                int unreads = count == null ? 0 : count.get(1).asInt() + count.get(2).asInt();   // chat messages & private messages
                int highlights = count == null ? 0 : count.get(3).asInt();                       // highlights
                buffer.updateHighlightsAndUnreads(highlights, unreads);
            }

            onHotlistFinished();
            notifyBuffersSlightlyChanged();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // _buffer_line_added
    // listlines_reverse
    static RelayMessageHandler bufferLineWatcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("bufferLineWatcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            HashSet<Buffer> freshBuffers = new HashSet<>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                boolean isBottom = id.equals("_buffer_line_added");

                long buffer_pointer = (isBottom) ? entry.getItem("buffer").asPointerLong() : entry.getPointerLong(0);
                Buffer buffer = findByPointer(buffer_pointer);
                if (buffer == null) {
                    if (DEBUG_HANDLERS) logger.warn("bufferLineWatcher: no buffer to update!");
                    continue;
                }
                if (!isBottom)
                    freshBuffers.add(buffer);
                String message = entry.getItem("message").asString();
                String prefix = entry.getItem("prefix").asString();
                boolean displayed = (entry.getItem("displayed").asChar() == 0x01);
                Date time = entry.getItem("date").asTime();
                RelayObject high = entry.getItem("highlight");
                boolean highlight = (high != null && high.asChar() == 0x01);
                RelayObject tagsobj = entry.getItem("tags_array");

                String[] tags = (tagsobj != null && tagsobj.getType() == RelayObject.WType.ARR) ?
                        tagsobj.asArray().asStringArray() : null;

                Buffer.Line line = new Buffer.Line(entry.getPointerLong(), time, prefix, message, displayed, highlight, tags);
                buffer.addLine(line, isBottom);

            }

            for (Buffer buffer : freshBuffers) buffer.onLinesListed();
        }
    };

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
    static RelayMessageHandler nickListWatcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("nickListWatcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            boolean diff = id.equals("_nicklist_diff");
            HashSet<Buffer> renickedBuffers = new HashSet<>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                // find buffer
                Buffer buffer = findByPointer(entry.getPointerLong(0));
                if (buffer == null) {
                    if (DEBUG_HANDLERS) logger.warn("nickListWatcher: no buffer to update!");
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
                        if (command == ADD)
                            buffer.addNick(pointer, prefix, name);
                        else
                            buffer.updateNick(pointer, prefix, name);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// saving/restoring stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** restore buffer's stuff. this is called for every buffer upon buffer creation */
    synchronized static void restoreLastReadLine(Buffer buffer) {
        BufferHotData data = bufferToLastReadLine.get(buffer.fullName);
        if (data != null) {
            buffer.lastReadLine = data.lastReadLine;
            buffer.totalReadUnreads = data.totalOldUnreads;
            buffer.totalReadHighlights = data.totalOldHighlights;
        }
    }

    /** save buffer's stuff. this is called when information is about to be written to disk */
    synchronized static void saveLastReadLine(Buffer buffer) {
        BufferHotData data = bufferToLastReadLine.get(buffer.fullName);
        if (data == null) {
            data = new BufferHotData();
            bufferToLastReadLine.put(buffer.fullName, data);
        }
        data.lastReadLine = buffer.lastReadLine;
        data.totalOldUnreads = buffer.totalReadUnreads;
        data.totalOldHighlights = buffer.totalReadHighlights;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized static @Nullable String getSyncedBuffersAsString() {
        if (DEBUG_SAVE_RESTORE) logger.warn("getSyncedBuffersAsString() -> ...");
        return Utils.serialize(syncedBuffersFullNames);
    }

    @SuppressWarnings("unchecked")
    synchronized static void setSyncedBuffersFromString(@Nullable String syncedBuffers) {
        if (DEBUG_SAVE_RESTORE) logger.warn("setSyncedBuffersFromString(...)");
        Object o = Utils.deserialize(syncedBuffers);
        if (o instanceof LinkedHashSet)
            syncedBuffersFullNames = (LinkedHashSet<String>) o;
    }

    synchronized static @Nullable String getBufferToLastReadLineAsString() {
        if (DEBUG_SAVE_RESTORE) logger.warn("getBufferToLastReadLineAsString() -> ...");
        if (buffers != null) for (Buffer buffer : buffers) saveLastReadLine(buffer);
        return Utils.serialize(bufferToLastReadLine);
    }

    @SuppressWarnings("unchecked")
    synchronized static void setBufferToLastReadLineFromString(@Nullable String buffersReadLines) {
        if (DEBUG_SAVE_RESTORE) logger.warn("setBufferToLastReadLineFromString(...)");
        Object o = Utils.deserialize(buffersReadLines);
        if (o instanceof LinkedHashMap)
            bufferToLastReadLine = (LinkedHashMap<String, BufferHotData>) o;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class BufferHotData implements Serializable {
        long lastReadLine = -1;
        int totalOldUnreads = 0;
        int totalOldHighlights = 0;
    }
}
