package com.ubergeek42.WeechatAndroid.service;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/** a class that holds information about buffers
 ** probably should be made static */

public class BufferList {
    private static Logger logger = LoggerFactory.getLogger("BufferList");
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_HANDLERS = false;

    // preferences
    public static boolean SORT_BUFFERS = false;
    public static boolean SHOW_TITLE = true;
    public static boolean FILTER_NONHUMAN_BUFFERS = false;
    public static String FILTER = null;                                                            // TODO race condition?

    final static public LinkedHashSet<String> synced_buffers_full_names = new LinkedHashSet<String>();  // TODO race condition?

    public int hot_count = 0;
    static Buffer.Line last_hot_line = null;
    static Buffer last_hot_buffer = null;

    final RelayService relay;
    final private RelayConnection connection;
    final private ArrayList<Buffer> buffers = new ArrayList<Buffer>();

    private BufferListEye buffers_eye;

    BufferList(RelayService relay) {
        this.relay = relay;
        this.connection = this.relay.connection;
        Buffer.buffer_list = this;

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
        connection.sendMsg("hotlist", "hdata", "hotlist:gui_hotlist(*) buffer,count");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public @NonNull ArrayList<Buffer> getBufferListCopy() {
        sortBuffers();
        ArrayList<Buffer> new_bufs = new ArrayList<Buffer>();
        for (Buffer buffer : buffers) {
            if (FILTER_NONHUMAN_BUFFERS && buffer.type == Buffer.OTHER && buffer.highlights == 0 && buffer.unreads == 0) continue;
            if (FILTER != null && !buffer.full_name.toLowerCase().contains(FILTER)) continue;
            new_bufs.add(buffer);
        }
        return new_bufs;
    }

    synchronized public @Nullable Buffer findByFullName(@Nullable String full_name) {
        if (full_name == null) return null;
        for (Buffer buffer : buffers) if (buffer.full_name.equals(full_name)) return buffer;
        return null;
    }

    /** sets or remove (using null) buffer list change watcher */
    synchronized public void setBufferListEye(@Nullable BufferListEye buffers_eye) {
        this.buffers_eye = buffers_eye;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// called on the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////    from this and Buffer (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////    (also alert Buffer)

    /** called when a buffer has been added or removed */
    synchronized private void notifyBuffersChanged() {
        checkIfHotCountHasChanged();
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
    }

    /** called when buffer data has been changed, but the no of buffers is the same
     ** other_messages_changed signifies if buffer type is OTHER and message count has changed
     ** used to temporarily display the said buffer if OTHER buffers are filtered */
    synchronized void notifyBuffersSlightlyChanged(boolean other_messages_changed) {
        checkIfHotCountHasChanged();
        if (buffers_eye != null) {
            if (other_messages_changed && FILTER_NONHUMAN_BUFFERS)
                buffers_eye.onBuffersChanged();
            else
                buffers_eye.onBuffersSlightlyChanged();
        }
    }

    synchronized void notifyBuffersSlightlyChanged() {
        notifyBuffersSlightlyChanged(false);
    }

    /** called when no buffers has been added or removed, but
     ** buffer changes are such that we should reorder the buffer list */
    synchronized private void notifyBufferPropertiesChanged(Buffer buffer) {
        buffer.onPropertiesChanged();
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
    }

    /** process all open buffers and, if specified, notify them of the change
     ** practically notifying is only needed when pressing volume up/dn keys,
     ** which means we are not in the preferences window and the activity will not
     ** get re-rendered */
    synchronized void notifyOpenBuffersMustBeProcessed(boolean notify) {
        for (Buffer buffer : buffers)
            if (buffer.is_open) {
                buffer.forceProcessAllMessages();
                if (notify) buffer.onLinesChanged();
            }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized boolean isSynced(String full_name) {
        return synced_buffers_full_names.contains(full_name);
    }

    synchronized void syncBuffer(String full_name) {
        if (DEBUG) logger.warn("syncBuffer({})", full_name);
        BufferList.synced_buffers_full_names.add(full_name);
        relay.connection.sendMsg("sync " + full_name);
    }

    synchronized void desyncBuffer(String full_name) {
        if (DEBUG) logger.warn("desyncBuffer({})", full_name);
        BufferList.synced_buffers_full_names.remove(full_name);
        relay.connection.sendMsg("desync " + full_name);
    }

    synchronized static String getSyncedBuffersAsString() {
        if (DEBUG) logger.error("getSyncedBuffersAsString() -> ...");
        StringBuilder sb = new StringBuilder();
        for (String full_name : BufferList.synced_buffers_full_names)
            sb.append(full_name).append("\0");
        return sb.toString();
    }

    synchronized static void setSyncedBuffersFromString(String synced_buffers) {
        if (DEBUG) logger.warn("setSyncedBuffersFromString({})", synced_buffers);
        StringTokenizer st = new StringTokenizer(synced_buffers, "\0");
        while (true) {
            try {
                synced_buffers_full_names.add(st.nextToken());
            } catch (NoSuchElementException e) {
                return;
            }
        }
    }

    public void requestLinesForBufferByPointer(int pointer) {
        if (DEBUG) logger.warn("requestLinesForBufferByPointer({})", pointer);
        connection.sendMsg("listlines_reverse", "hdata", String.format(
                "buffer:0x%x/own_lines/last_line(-%d)/data date,displayed,prefix,message,highlight,notify,tags_array",
                pointer, Buffer.MAX_LINES));
    }

    public void requestNicklistForBufferByPointer(int pointer) {
        if (DEBUG) logger.warn("requestNicklistForBufferByPointer({})", pointer);
        connection.sendMsg(String.format("(nicklist) nicklist 0x%x", pointer));
    }

    synchronized static void setMostRecentHotLine(@NonNull Buffer buffer, @NonNull Buffer.Line line) {
        last_hot_buffer = buffer;
        last_hot_line = line;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void checkIfHotCountHasChanged() {
        if (DEBUG) logger.warn("checkIfHotCountHasChanged()");
        int hot = 0;
        for (Buffer buffer : buffers) {
            hot += buffer.highlights;
            if (buffer.type == Buffer.PRIVATE) hot += buffer.unreads;
        }
        if (hot != hot_count) {
            boolean new_highlight = hot > hot_count;
            boolean must_switch_icon = hot == 0 || hot_count == 0;
            hot_count = hot;
            if (buffers_eye != null)
                buffers_eye.onHotCountChanged();
            if (new_highlight) {
                relay.changeNotification(true, hot, last_hot_buffer, last_hot_line);
                last_hot_buffer = null;
                last_hot_line = null;
            } else if (must_switch_icon)
                relay.changeNotification(false, hot, null, null);
        }
    }

    synchronized private @Nullable Buffer findByPointer(int pointer) {
        for (Buffer buffer : buffers) if (buffer.pointer == pointer) return buffer;
        return null;
    }

    /** compares buffers by their number */
    private final Comparator<Buffer> sortByNumberComparator = new Comparator<Buffer>() {
        @Override public int compare(Buffer b1, Buffer b2) {
            return b1.number - b2.number;
        }
    };

    synchronized private void sortBuffers() {
        Collections.sort(buffers, sortByNumberComparator);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// yay!! message handlers!! the joy
    //////////////////////////////////////////////////////////////////////////////////////////////// buffer list
    ////////////////////////////////////////////////////////////////////////////////////////////////

    RelayMessageHandler buffer_list_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.warn("handleMessage(..., {}) (hdata size = {})", id, ((Hdata) obj).getCount());
            Hdata data = (Hdata) obj;

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                if (id.equals("listbuffers") || id.equals("_buffer_opened")) {
                    RelayObject r;
                    Buffer buffer = new Buffer(entry.getPointerInt(),
                            entry.getItem("number").asInt(),
                            entry.getItem("full_name").asString(),
                            entry.getItem("short_name").asString(),
                            entry.getItem("title").asString(),
                            ((r = entry.getItem("notify")) != null) ? r.asInt() : 1,            // TODO request notify level afterwards???
                            (Hashtable) entry.getItem("local_variables"));                      // TODO because _buffer_opened doesn't provide notify level
                    synchronized (BufferList.this) {buffers.add(buffer);}
                    notifyBuffersChanged();
                } else {
                    Buffer buffer = findByPointer(entry.getPointerInt(0));
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
                            buffer.number = entry.getItem("number").asInt();
                            notifyBufferPropertiesChanged(buffer);
                        } else if (id.equals("_buffer_closing")) {
                            buffer.onBufferClosed();
                            synchronized (BufferList.this) {buffers.remove(buffer);}
                            notifyBuffersChanged();
                        } else {
                            if (DEBUG_HANDLERS) logger.warn("Unknown message ID: '{}'", id);
                        }
                    }
                }
            }
        }
    };

    //////////////////////////////////////////////////////////////////////////////////////////////// hotlist

    // hotlist (ONLY)
    RelayMessageHandler hotlist_init_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("hotlist_init_watcher:handleMessage(..., {})", id);
            Hdata data = (Hdata) obj;
            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                Integer pointer = entry.getItem("buffer").asPointerInt();
                Buffer buffer = findByPointer(pointer);
                if (buffer != null) {
                    Array count = entry.getItem("count").asArray();
                    buffer.unreads = count.get(1).asInt() + count.get(2).asInt();   // chat messages & private messages
                    buffer.highlights = count.get(3).asInt();                       // highlights
                }
            }
            notifyBuffersSlightlyChanged();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // _buffer_line_added
    // listlines_reverse
    RelayMessageHandler buffer_line_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("buffer_line_watcher:handleMessage(..., {})", id);
            Hdata data = (Hdata) obj;

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);
                boolean is_bottom = id.equals("_buffer_line_added");

                int buffer_pointer = (is_bottom) ? entry.getItem("buffer").asPointerInt() : entry.getPointerInt(0);
                Buffer buffer = findByPointer(buffer_pointer);
                if (buffer == null) {
                    if (DEBUG_HANDLERS) logger.warn("buffer_line_watcher: no buffer to update!");
                    continue;
                }
                String message = entry.getItem("message").asString();
                String prefix = entry.getItem("prefix").asString();
                boolean displayed = (entry.getItem("displayed").asChar() == 0x01);
                Date time = entry.getItem("date").asTime();
                RelayObject high = entry.getItem("highlight");
                boolean highlight = (high != null && high.asChar() == 0x01);
                RelayObject tagsobj = entry.getItem("tags_array");

                String[] tags = (tagsobj != null && tagsobj.getType() == RelayObject.WType.ARR) ?
                        tagsobj.asArray().asStringArray() : null;

                Buffer.Line line = new Buffer.Line(entry.getPointerInt(), time, prefix, message, displayed, highlight, tags);
                buffer.addLine(line, is_bottom);

                if (!is_bottom) buffer.holds_all_lines_it_is_supposed_to_hold = true;
            }
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
    RelayMessageHandler nicklist_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            if (DEBUG_HANDLERS) logger.debug("nicklist_watcher:handleMessage(..., {})", id);
            Hdata data = (Hdata) obj;
            boolean diff = id.equals("_nicklist_diff");
            HashSet<Buffer> renicked_buffers = new HashSet<Buffer>();

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                // find buffer
                Buffer buffer = findByPointer(entry.getPointerInt(0));
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
                        int pointer = entry.getPointerInt();
                        String prefix = entry.getItem("prefix").asString();
                        String name = entry.getItem("name").asString();
                        if (command == ADD)
                            buffer.addNick(pointer, prefix, name);
                        else
                            buffer.updateNick(pointer, prefix, name);
                    }
                } else if (command == REMOVE) {
                    buffer.removeNick(entry.getPointerInt());
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
}