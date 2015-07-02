/*******************************************************************************
 * Copyright 2012 Tor Hveem
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
package com.ubergeek42.weechat;

import java.util.HashMap;

import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class HotlistItem {
    /*
     * priority......................: int 1 color.........................: str 'white'
     * creation_time.................: buf buffer_pointer................: ptr 0x227f5b0
     * bufferNumber.................: int 3 pluginName...................: str 'irc'
     * bufferName...................: str 'network.#channel' count00......................: int 0
     * count01......................: int 2 count02......................: int 0
     * count03......................: int 0
     */
    public int priority;
    public String color;
    public String buffer;
    public int bufferNumber;
    public String pluginName;
    public String bufferName;
    public int count00;
    public int count01;
    public int count02;
    public int count03;

    public HotlistItem(HashMap<String, RelayObject> item) {

        this.priority = item.get("priority").asInt();
        this.color = item.get("color").asString();
        this.buffer = item.get("buffer_pointer").asPointer();
        this.bufferNumber = item.get("buffer_number").asInt();
        this.pluginName = item.get("plugin_name").asString();
        this.bufferName = item.get("buffer_name").asString();
        this.count00 = item.get("count_00").asInt();
        this.count01 = item.get("count_01").asInt();
        this.count02 = item.get("count_02").asInt();
        this.count03 = item.get("count_03").asInt();

    }

    public HotlistItem(HdataEntry hde, Buffer b) {
        // Get the information about the "line"
        String bPointer = hde.getItem("buffer").asPointer();

        // Is line displayed or hidden by filters, etc?
        boolean displayed = (hde.getItem("displayed").asChar() == 0x01);

        // Try to get highlight status(added in 0.3.8-dev: 2012-03-06)
        RelayObject t = hde.getItem("highlight");
        boolean highlight = false;
        if (t != null) {
            highlight = (t.asChar() == 0x01);
        }

        // TODO: should be based on tags for line(notify_none/etc), but these are inaccessible
        // through the relay plugin
        // Determine if buffer is a privmessage(check localvar "type" for value "private"), and
        // notify for that too
        RelayObject bufferType = b.getLocalVar("type");
        if (bufferType != null && bufferType.asString().equals("private")) {
            // Must have localvar("channel") == prefix
            RelayObject buddyNick = b.getLocalVar("channel");
            if (buddyNick != null
                    && buddyNick.asString().equals(
                            Color.stripColors(hde.getItem("prefix").asString()))) {
                highlight = true;
            }
        }
        this.bufferNumber = b.getNumber();
        this.buffer = bPointer;
        this.bufferName = b.getFullName();
        // FIXME get plugin name from buffer
        this.pluginName = "";
        this.count00 = 0;
        this.count02 = 0;
        this.count01 = 0;
        if (highlight) {
            if (displayed) {
                this.count02 = 1;
            } else {
                this.count00 = 1;
            }
        } else {
            if (displayed) {
                this.count01 = 1;
            } else {
                this.count00 = 1;
            }
        }
    }

    public String getFullName() {
        if (this.pluginName != "") {
            return this.pluginName + "." + this.bufferName;
        }
        return this.bufferName;
    }

    public int getUnread() {
        return this.count01;
    }

    public int getHighlights() {
        return this.count02;
    }

    @Override
    public String toString() {
        return bufferName;
    }
}
