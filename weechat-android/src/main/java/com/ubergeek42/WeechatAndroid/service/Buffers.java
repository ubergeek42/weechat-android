package com.ubergeek42.WeechatAndroid.service;

import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by sq on 25/07/2014.
 */
public class Buffers {
    private static Logger logger = LoggerFactory.getLogger("Buffers!");
    final private static boolean DEBUG = false;

    final private RelayServiceBackbone bone;
    final private RelayConnection connection;
    final private ArrayList<Buffer> buffers = new ArrayList<Buffer>();

    private BuffersEye buffers_eye;

    Buffers(RelayServiceBackbone bone) {
        this.bone = bone;
        this.connection = bone.connection;

        // Handle us getting a listing of the this
        connection.addHandler("listbuffers", buffer_list_watcher);

        // Handle weechat event messages regarding this
        connection.addHandler("_buffer_opened", buffer_list_watcher);
        connection.addHandler("_buffer_renamed", buffer_list_watcher);
        connection.addHandler("_buffer_title_changed", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_added", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_changed", buffer_list_watcher);
        connection.addHandler("_buffer_localvar_removed", buffer_list_watcher);
        connection.addHandler("_buffer_closing", buffer_list_watcher);

        connection.addHandler("_buffer_line_added", buffer_line_watcher);
        connection.addHandler("listlines_reverse", buffer_line_watcher);

        // get a list of buffers current open, along with some information about them
        // also nicklist
        connection.sendMsg("listbuffers", "hdata", "buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables,notify");
        connection.sendMsg("hotlist", "hdata", "hotlist:gui_hotlist(*) buffer,count");
    }

    synchronized public ArrayList<Buffer> getBuffersCopy() {return new ArrayList<Buffer>(buffers);}

    synchronized public Buffer findByPointer(int pointer) {
        for (Buffer buffer : buffers) if (buffer.pointer == pointer) return buffer;
        return null;
    }

    synchronized public Buffer findByFullName(String name) {
        for (Buffer buffer : buffers) {
            if (buffer.full_name.equals(name)) return buffer;
        }
        return null;
    }

    synchronized public void setBuffersEye(BuffersEye buffers_eye) {this.buffers_eye = buffers_eye;}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized private void notifyBuffersChanged() {
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
    }

    synchronized public void notifyBufferPropertiesChanged(Buffer buffer) {
        if (buffers_eye != null) buffers_eye.onBuffersChanged();
        buffer.onPropertiesChanged();
    }

    RelayMessageHandler buffer_list_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            Hdata data = (Hdata) obj;

            for (int i = 0, size = data.getCount(); i < size; i++) {
                HdataEntry entry = data.getItem(i);

                if (id.equals("listbuffers") || id.equals("_buffer_opened")) {
                    Buffer buffer = new Buffer(entry.getPointerInt(),
                            entry.getItem("number").asInt(),
                            entry.getItem("full_name").asString(),
                            entry.getItem("short_name").asString(),
                            entry.getItem("title").asString(),
                            entry.getItem("notify").asInt(),
                            (Hashtable) entry.getItem("local_variables"));
                    buffers.add(buffer);
                    notifyBuffersChanged();
                } else if (id.equals("hotlist")) {
                    String buffer_pointer = entry.getItem("buffer").asPointer();
                    String[] count = entry.getItem("count").asArray().asStringArray();
                    // update buffer
                    // setting from_human (01)
                    // & highlight (02)
                } else {
                    Buffer buffer = findByPointer(entry.getPointerInt(0));
                    if (buffer == null) throw new AssertionError("no buffer to update!!");
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
                    } else if (id.equals("_buffer_closing")) {
                        buffer.onBufferClosed();
                        buffers.remove(buffer);
                        notifyBuffersChanged();
                    } else {
                        if (DEBUG) logger.warn("Unknown message ID: '{}'", id);
                    }
                }

            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    RelayMessageHandler buffer_line_watcher = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            Hdata data = (Hdata) obj;

            for (int i = 0, size = data.getCount(); i < size; i++) {

                boolean is_bottom = id.equals("_buffer_line_added");

                HdataEntry entry = data.getItem(i);
                int buffer_pointer = (is_bottom) ? entry.getItem("buffer").asPointerInt() :  entry.getPointerInt(0);
                Buffer buffer = findByPointer(buffer_pointer);
                if (buffer == null) {
                    if (DEBUG) logger.warn("no buffer to update!");
                    continue;
                }
                String message = entry.getItem("message").asString();
                String prefix =  entry.getItem("prefix").asString();
                boolean displayed = ( entry.getItem("displayed").asChar() == 0x01);
                Date time =  entry.getItem("date").asTime();
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
}
























