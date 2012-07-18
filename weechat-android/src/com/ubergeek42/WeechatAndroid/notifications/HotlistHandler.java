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
package com.ubergeek42.WeechatAndroid.notifications;

import java.util.ArrayList;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class HotlistHandler implements RelayMessageHandler {
	//private static Logger logger = LoggerFactory.getLogger(HotlistHandler.class);
	private BufferManager bufferManager;
	private ArrayList<HotlistObserver> observers = new ArrayList<HotlistObserver>();
	
	public HotlistHandler(BufferManager bufferManager) {
		this.bufferManager = bufferManager;
	}

	public void registerHighlightHandler(HotlistObserver observer) {
		if (observers.contains(observer)) return;
		observers.add(observer);
	}
	public void unRegisterHighlightHandler(HotlistObserver observer) {
		observers.remove(observer);
	}

	@Override
	public void handleMessage(RelayObject obj, String id) {
		if (id.equals("hotlist")) { // Results from "infolist hotlist"
			// TODO: generate "Hotlist Status" string similar to at the bottom of weechat

		} else if (id.equals("_buffer_line_added")){ // New line added...what is it?
			//logger.debug("buffer_line_added called");
			Hdata hdata = (Hdata) obj;
			for(int i=0;i<hdata.getCount(); i++) {
				HdataEntry hde = hdata.getItem(i);
				hde.getItem("buffer");
				
				String bPointer = hde.getItem("buffer").asPointer();
				
				// Try to get highlight status(added in 0.3.8-dev: 2012-03-06)
				RelayObject t = hde.getItem("highlight");
				boolean highlight = false;
				if(t!=null) highlight = (t.asChar()==0x01);

				
				// Find the associated buffer
				Buffer b = bufferManager.findByPointer(bPointer);
				if(b==null) {
					return;
				}

				// TODO: should be based on tags for line(notify_none/etc), but these are inaccessible through the relay plugin
				// Determine if buffer is a privmessage(check localvar "type" for value "private"), and notify for that too
				RelayObject bufferType = b.getLocalVar("type");
				if (bufferType != null && bufferType.asString().equals("private")) {
					// Must have localvar("channel") == prefix
					RelayObject buddyNick = b.getLocalVar("channel");
					if (buddyNick != null && buddyNick.asString().equals(Color.stripColors(hde.getItem("prefix").asString()))) {	
						highlight = true;
					}
				}
				
				// Nothing to do if not a highlight
				if (!highlight)
					return;
				
				// Update the buffer to indicate there are unread highlights
				b.addHighlight();
				bufferManager.buffersChanged();
				// Send the notification
				String notificationText = Color.stripColors(hde.getItem("prefix").asString()) + ": " + Color.stripColors(hde.getItem("message").asString());
				notificationText = Color.stripColors(notificationText);
				for(HotlistObserver o: observers) {
					o.onHighlight(b.getFullName(), notificationText);
				}
			}
		}
	}
	/*
	 * Example hotlist infolist content; counts are msg, private, highlight, other
[Infolist] hotlist
  Item 0
    count_01: 0
    count_00: 0
    count_03: 0
    count_02: 1
    color: "lightgreen"
    buffer_pointer: 0xdb0378
    priority: 2
    creation_time: [82, 65, 102, 79, 26, -9, 5, 0]
    plugin_name: "relay"
    buffer_number: 10
    buffer_name: "relay.list"
  Item 1
    count_01: 0
    count_00: 106
    count_03: 0
    count_02: 0
    color: "default"
    buffer_pointer: 0xfdb238
    priority: 0
    creation_time: [-27, 52, 102, 79, 103, -90, 9, 0]
    plugin_name: "relay"
    buffer_number: 11
    buffer_name: "relay_raw"
    
	 */
}
