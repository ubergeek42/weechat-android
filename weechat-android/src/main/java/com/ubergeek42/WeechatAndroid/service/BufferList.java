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

    final private static int SYNC_LAST_READ_LINE_EVERY_MS = 60 * 5 * 1000; // 5 minutes

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
    static public @NonNull LinkedHashSet<String> synced_buffers_full_names = new LinkedHashSet<String>();   // TODO race condition?

    /** this stores information about last read line (in `desktop` weechat) and according
     ** number of read lines/highlights. this is substracted from highlight counts client
     ** receives from the server */
    static private @NonNull LinkedHashMap<String, BufferHotData> buffer_to_last_read_line = new LinkedHashMap<String, BufferHotData>();

    static RelayService relay;
    private static RelayConnection connection;
    private static BufferListEye buffers_eye;

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
        connection.addHandler("listbuffers", buffer_list_watcher);

        connection.addHandler("_buffer_opened", buffer_list_watcher);
        connection.addHandler("_buffer_renamed", buffer_list_watcher);
        connection.addHandler("_buffer_title_changed", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_added", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_changed", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_removed", buffer_list_watcher);
        connection.addHandler("_buffer_closing", buffer_list_watcher);
        connection.addHandler("_buffer_moved", buffer_list_watcher);
        connection.addHandler("_buffer_merged", buffer_list_watcher);

        connection.addHandler("hotlist", hotlist_init_watcher);
        connection.addHandler("last_read_lines", last_read_lines_watcher);

        // handle newly arriving chat lines
        // and chatlines we are reading in reverse
        connection.addHandler("_buffer_line_added", buffer_line_watcher);
        connection.addHandler("listlines_reverse", buffer_line_watcher);

        // handle nicklist init and changes
        connection.addHandler("nicklist", nicklist_watcher);
        connection.addHandler("_nicklist", nicklist_watcher);
        connection.addHandler("_nicklist_diff", nicklist_watcher);

        // request a list of buffers current open, along with some information about them
        // also request hotlist
        // and nicklist
        connection.sendMsg("listbuffers", "hdata", "buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables,notify");
        connection.sendMsg("last_read_lines", "hdata", "buffer:gui_buffers(*)/own_lines/last_read_line/data buffer");
        connection.sendMsg("hotlist", "hdata", "hotlist:gui_hotlist(*) buffer,count");
        relay.thandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connection.sendMsg("last_read_lines", "hdata", "buffer:gui_buffers(*)/own_lines/last_read_line/data buffer");
                connection.sendMsg("hotlist", "hdata", "hotlist:gui_hotlist(*) buffer,count");
                relay.thandler.postDelayed(this, SYNC_LAST_READ_LINE_EVERY_MS);
            }
        }, SYNC_LAST_READ_LINE_EVERY_MS);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static @Nullable ArrayList<Buffer> sent_buffers = null;

    /** returns an independent copy of the buffer list
     ** MIGHT return the same object (albeit sorted as needed) */
    synchronized static public @NonNull ArrayList<Buffer> getBufferList() {
        if (sent_buffers == null) {
            sent_buffers = new ArrayList<Buffer>();
            for (Buffer buffer : buffers) {
                if (buffer.type == Buffer.HARD_HIDDEN) continue;
                if (FILTER_NONHUMAN_BUFFERS && buffer.type == Buffer.OTHER && buffer.highlights == 0 && buffer.unreads == 0) continue;
                if (FILTER_LC != null && FILTER_UC != null && !buffer.full_name.toLowerCase().contains(FILTER_LC) && !buffer.full_name.toUpperCase().contains(FILTER_UC)) continue;
                sent_buffers.add(buffer);
            }
        }
        if (SORT_BUFFERS) Collections.sort(sent_buffers, sortByHotAndMessageCountComparator);
        else Collections.sort(sent_buffers, sortByHotCountAndNumberComparator);
        return sent_buffers;
    }

    synchronized static public void setFilter(String filter) {
        FILTER_LC = (filter.length() == 0) ? null : filter.toLowerCase();
        FILTER_UC = (filter.length() == 0) ? null : filter.toUpperCase();
        sent_buffers = null;
    }

    synchronized static public @Nullable Buffer findByFullName(@Nullable String full_name) {
        if (full_name == null) return null;
        for (Buffer buffer : buffers) if (buffer.full_name.equals(full_name)) return buffer;
        return null;
    }

    /** sets or remove (using null) buffer list change watcher */
    synchronized static public void setBufferListEye(@Nullable BufferListEye buffers_eye) {
        BufferList.buffers_eye = buffers_eye;
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
        sent_buffers = null;
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
    }

    /** called when buffer data has been changed, but the no of buffers is the same
     ** other_messages_changed signifies if buffer type is OTHER and message count has changed
     ** used to temporarily display the said buffer if OTHER buffers are filtered */
    synchronized static void notifyBuffersSlightlyChanged(boolean other_messages_changed) {
        if (buffers_eye != null) {
            if (other_messages_changed && FILTER_NONHUMAN_BUFFERS) sent_buffers = null;
            buffers_eye.onBuffersChanged();
        }
    }

    synchronized static void notifyBuffersSlightlyChanged() {
        notifyBuffersSlightlyChanged(false);
    }

    /** called when no buffers has been added or removed, but
     ** buffer changes are such that we should reorder the buffer list */
    synchronized static private void notifyBufferPropertiesChanged(Buffer buffer) {
        buffer.onPropertiesChanged();
        sent_buffers = null;
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
    }

    /** process all open buffers and, if specified, notify them of the change
     ** practically notifying is only needed when pressing volume up/dn keys,
     ** which means we are not in the preferences window and the activity will not
     ** get re-rendered */
    synchronized static void notifyOpenBuffersMustBeProcessed(boolean notify) {
        for (Buffer buffer : buffers)
            if (buffer.is_open) {
                buffer.forceProcessAllMessages();
                if (notify) buffer.onLinesChanged();
            }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized static boolean isSynced(String full_name) {
        return synced_buffers_full_names.contains(full_name);
    }

    /** send sync command to relay (if traffic is set to optimized) and
     ** add it to the synced buffers (=open buffers) list */
    synchronized static void syncBuffer(String full_name) {
        if (DEBUG_SYNCING) logger.warn("syncBuffer({})", full_name);
        BufferList.synced_buffers_full_names.add(full_name);
        if (OPTIMIZE_TRAFFIC) relay.connection.sendMsg("sync " + full_name);
    }

    synchronized static void desyncBuffer(String full_name) {
        if (DEBUG_SYNCING) logger.warn("desyncBuffer({})", full_name);
        BufferList.synced_buffers_full_names.remove(full_name);
        if (OPTIMIZE_TRAFFIC) relay.connection.sendMsg("desync " + full_name);
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
    static public ArrayList<String[]> hot_list = new ArrayList<String[]>();

    /** this is the value calculated from hotlist received from weechat
     ** it MIGHT BE GREATER than size of hot_list
     ** initialized, it's -1, so that on service restart we know to remove notification 43 */
    static private int hot_count = -1;

    /** returns hot count or 0 if unknown */
    static public int getHotCount() {
        return hot_count == -1 ? 0 : hot_count;
    }

    /** called when a new new hot message just arrived */
    synchronized static void newHotLine(final @NonNull Buffer buffer, final @NonNull Buffer.Line line) {
        hot_list.add(new String[]{buffer.full_name, line.getNotificationString()});
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(true);
    }

    /** called when buffer is read or closed
     ** must be called AFTER buffer hot count adjustments / buffer removal from the list */
    synchronized static void removeHotMessagesForBuffer(final @NonNull Buffer buffer) {
        for (Iterator<String[]> it = hot_list.iterator(); it.hasNext(); )
            if (it.next()[0].equals(buffer.full_name)) it.remove();
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(false);
    }

    /** remove a number of messages for a given buffer, leaving last 'leave' messages
     ** DOES NOT notify anyone of the change */
    synchronized static void adjustHotMessagesForBuffer(final @NonNull Buffer buffer, int leave) {
        for (ListIterator<String[]> it = hot_list.listIterator(hot_list.size()); it.hasPrevious();) {
            if (it.previous()[0].equals(buffer.full_name) && (leave-- <= 0))
                it.remove();
        }
    }

    synchronized static void onHotlistFinished() {
        if (processHotCountAndTellIfChanged())
            notifyHotCountChanged(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** HELPER. stores hot_count;
     ** returns true if hot count has changed */
    static private boolean processHotCountAndTellIfChanged() {
        int hot = 0;
        for (Buffer buffer : buffers) {
            hot += buffer.highlights;
            if (buffer.type == Buffer.PRIVATE) hot += buffer.unreads;
        }
        return hot_count != (hot_count = hot); // har har
    }

    /** HELPER. notifies everyone interested of hotlist changes */
    static private void notifyHotCountChanged(boolean new_highlight) {
        relay.changeHotNotification(new_highlight);
        if (buffers_eye != null)
            buffers_eye.onHotCountChanged();
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

    static RelayMessageHandler buffer_list_watcher = new RelayMessageHandler() {
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
                            buffer.full_name = entry.getItem("full_name").asString();
                            buffer.short_name = entry.getItem("short_name").asString();
                            buffer.local_vars = (Hashtable) entry.getItem("local_variables");
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_title_changed")) {
                            buffer.title = entry.getItem("title").asString();
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.startsWith("_buffer_localvar_")) {
                            buffer.local_vars = (Hashtable) entry.getItem("local_variables");
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
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // last_read_lines
    static RelayMessageHandler last_read_lines_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.error("last_read_lines_watcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;

            HashMap<Long, Long> buffer_to_lrl = new HashMap<Long, Long>();

            Hdata data = (Hdata) obj;
            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long buffer_pointer = entry.getItem("buffer").asPointerLong();
                long line_pointer = entry.getPointerLong();
                buffer_to_lrl.put(buffer_pointer, line_pointer);
            }

            synchronized (BufferList.class) {
                for (Buffer buffer : buffers) {
                    Long line_pointer = buffer_to_lrl.get(buffer.pointer);
                    buffer.updateLastReadLine(line_pointer == null ? -1 : line_pointer);
                }
            }
        }
    };

    // hotlist (ONLY)
    static RelayMessageHandler hotlist_init_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.error("hotlist_init_watcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;

            HashMap<Long, Array> buffer_to_hotlist = new HashMap<Long, Array>();

            Hdata data = (Hdata) obj;
            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                long pointer = entry.getItem("buffer").asPointerLong();
                Array count = entry.getItem("count").asArray();
                buffer_to_hotlist.put(pointer, count);
            }

            for (Buffer buffer: buffers) {
                Array count = buffer_to_hotlist.get(buffer.pointer);
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
    static RelayMessageHandler buffer_line_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("buffer_line_watcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            HashSet<Buffer> fresh_buffers = new HashSet<Buffer>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                boolean is_bottom = id.equals("_buffer_line_added");

                long buffer_pointer = (is_bottom) ? entry.getItem("buffer").asPointerLong() : entry.getPointerLong(0);
                Buffer buffer = findByPointer(buffer_pointer);
                if (buffer == null) {
                    if (DEBUG_HANDLERS) logger.warn("buffer_line_watcher: no buffer to update!");
                    continue;
                }
                if (!is_bottom)
                    fresh_buffers.add(buffer);
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
                buffer.addLine(line, is_bottom);

            }

            for (Buffer buffer : fresh_buffers) buffer.onLinesListed();
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
    static RelayMessageHandler nicklist_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("nicklist_watcher:handleMessage(..., {})", id);
            if (!(obj instanceof Hdata)) return;
            Hdata data = (Hdata) obj;
            boolean diff = id.equals("_nicklist_diff");
            HashSet<Buffer> renicked_buffers = new HashSet<Buffer>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                // find buffer
                Buffer buffer = findByPointer(entry.getPointerLong(0));
                if (buffer == null) {
                    if (DEBUG_HANDLERS) logger.warn("nicklist_watcher: no buffer to update!");
                    continue;
                }

                // if buffer doesn't hold all nicknames yet, break execution, since full nicks will be requested anyway later
                // erase nicklist if we have a full list here
                if (diff && !buffer.holds_all_nicks) continue;
                if (!diff && renicked_buffers.add(buffer)) buffer.removeAllNicks();

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
                for (Buffer buffer : renicked_buffers) {
                    buffer.holds_all_nicks = true;
                    buffer.sortNicksByLines();
                }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// saving/restoring stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** restore buffer's stuff. this is called for every buffer upon buffer creation */
    synchronized static void restoreLastReadLine(Buffer buffer) {
        BufferHotData data = buffer_to_last_read_line.get(buffer.full_name);
        if (data != null) {
            buffer.last_read_line = data.last_read_line;
            buffer.total_read_unreads = data.total_old_unreads;
            buffer.total_read_highlights = data.total_old_highlights;
        }
    }

    /** save buffer's stuff. this is called when information is about to be written to disk */
    synchronized static void saveLastReadLine(Buffer buffer) {
        BufferHotData data = buffer_to_last_read_line.get(buffer.full_name);
        if (data == null) {
            data = new BufferHotData();
            buffer_to_last_read_line.put(buffer.full_name, data);
        }
        data.last_read_line = buffer.last_read_line;
        data.total_old_unreads = buffer.total_read_unreads;
        data.total_old_highlights = buffer.total_read_highlights;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized static @Nullable String getSyncedBuffersAsString() {
        if (DEBUG_SAVE_RESTORE) logger.warn("getSyncedBuffersAsString() -> ...");
        return Utils.serialize(synced_buffers_full_names);
    }

    @SuppressWarnings("unchecked")
    synchronized static void setSyncedBuffersFromString(@Nullable String synced_buffers) {
        if (DEBUG_SAVE_RESTORE) logger.warn("setSyncedBuffersFromString(...)");
        Object o = Utils.deserialize(synced_buffers);
        if (o instanceof LinkedHashSet)
            synced_buffers_full_names = (LinkedHashSet<String>) o;
    }

    synchronized static @Nullable String getBufferToLastReadLineAsString() {
        if (DEBUG_SAVE_RESTORE) logger.warn("getBufferToLastReadLineAsString() -> ...");
        if (buffers != null) for (Buffer buffer : buffers) saveLastReadLine(buffer);
        return Utils.serialize(buffer_to_last_read_line);
    }

    @SuppressWarnings("unchecked")
    synchronized static void setBufferToLastReadLineFromString(@Nullable String buffers_read_lines) {
        if (DEBUG_SAVE_RESTORE) logger.warn("setBufferToLastReadLineFromString(...)");
        Object o = Utils.deserialize(buffers_read_lines);
        if (o instanceof LinkedHashMap)
            buffer_to_last_read_line = (LinkedHashMap<String, BufferHotData>) o;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class BufferHotData implements Serializable {
        long last_read_line = -1;
        int total_old_unreads = 0;
        int total_old_highlights = 0;
    }
}
