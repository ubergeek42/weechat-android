/*******************************************************************************
 * Copyright 2012 Keith Johnson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.weechat.relay.messagehandler;

import java.util.Date;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;
import com.ubergeek42.weechat.relay.protocol.RelayObject.WType;

public class LineHandler implements RelayMessageHandler {
    final private static boolean DEBUG = false;
    private static Logger logger = LoggerFactory.getLogger("LineHandler");

    private final BufferManager cb;

    public LineHandler(BufferManager cb) {
        this.cb = cb;
    }

    @Override
    public void handleMessage(RelayObject obj, String id) {
        if (DEBUG) logger.debug("handleMessage(..., {}): whdata.getCount() = {}", id, ((Hdata) obj).getCount());

        Buffer buffer = null;
        Hdata whdata = (Hdata) obj;
        HashSet<Buffer> toUpdate = new HashSet<Buffer>();

        for (int i = 0; i < whdata.getCount(); i++) {
            HdataEntry hde = whdata.getItem(i);
            // TODO: check last item of path is line_data

            // Get the information about the "line"
            String message = hde.getItem("message").asString();
            String prefix = hde.getItem("prefix").asString();
            boolean displayed = (hde.getItem("displayed").asChar() == 0x01);
            Date time = hde.getItem("date").asTime();
            String bPointer;
            if (id.equals("_buffer_line_added")) {
                bPointer = hde.getItem("buffer").asPointer();
            } else {
                bPointer = hde.getPointer(0);
            }

            // Try to get highlight status(added in 0.3.8-dev: 2012-03-06)
            RelayObject t = hde.getItem("highlight");
            boolean highlight = false;
            if (t != null) {
                highlight = (t.asChar() == 0x01);
            }

            String[] tags = null;
            // Try to get the array tags (added in 0.3.9-dev: 2012-07-23)
            // Make sure it is the right type as well, prior to this commit it is just a pointer
            RelayObject tagsobj = hde.getItem("tags_array");
            if (tagsobj != null && tagsobj.getType() == WType.ARR) {
                Array tagsArray = tagsobj.asArray();
                tags = tagsArray.asStringArray();
            }
            // Find the buffer to put the line in
            buffer = cb.findByPointer(bPointer);
            if (buffer == null) {
                logger.debug("Unable to find buffer to update");
                return;
            }
            // Do we already have this line?
            if (!buffer.hasLine(hde.getPointer())) {
                // Create a new message object, and add it to the correct buffer
                // #################### DEBUG
                //logger.warn("handleMessage(): new [{}] [{}]", prefix, message);
                // #################### DEBUG
                BufferLine cm = new BufferLine(hde.getPointer(), time, prefix, message, displayed, highlight, tags);
                if (id.equals("_buffer_line_added")) {
                    // Check if this line should be added as an unread line
                    // TODO make this into a preference as users might have
                    // different tastes
                    // TODO more elaborate checking of notify level
                    if (cm.isUnread() && buffer.getNotifyLevel() >= 1) {
                        buffer.addLine(cm);
                    } else {
                        buffer.addLineNoUnread(cm);
                    }
                    cb.buffersChanged();
                } else if (id.equals("listlines_reverse")) { // lines come in most recent to least recent
                    // TODO: check buffer isn't null...
                    buffer.addLineFirstNoNotify(cm);
                    //toUpdate.add(buffer);
                }
            }
        }
        // this loop probably should be written more neatly, but—
        // listlines_reverse usually comes in a bulk
        // after these were listed we can assume the buffer holds the maximum amount of lines
        // for the time being
        if (id.equals("listlines_reverse") && buffer != null) {
            buffer.holds_all_lines_it_is_supposed_to_hold = true;
            buffer.notifyManyLinesAdded();
        }
        for (Buffer wb : toUpdate) {
            wb.notifyLineAdded();
        }
    }
}
