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
import java.util.Arrays;
import java.util.List;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;
import com.ubergeek42.weechat.relay.protocol.RelayObject.WType;

public class HotlistHandler implements RelayMessageHandler {
	//private static Logger logger = LoggerFactory.getLogger(HotlistHandler.class);
	private BufferManager bufferManager;
	private HotlistManager hotlistManager;
	private ArrayList<HotlistObserver> observers = new ArrayList<HotlistObserver>();
	
	public HotlistHandler(BufferManager bufferManager, HotlistManager hotlistManager) {
		this.bufferManager = bufferManager;
		this.hotlistManager = hotlistManager;
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
		if (id.equals("_buffer_line_added")){ // New line added...what is it?
			//logger.debug("buffer_line_added called");
			Hdata hdata = (Hdata) obj;
			
			// Send the hdata to hotlist manager too, for hotlist update
			hotlistManager.handleMessage(obj, id);
			
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
					continue;
				}
				
				List tags = null;
				// Try to get the array tags (added in 0.3.9-dev: 2012-07-23)
				// Make sure it is the right type as well, prior to this commit it is just a pointer
				RelayObject tagsobj = hde.getItem("tags_array");
				if (tagsobj != null && tagsobj.getType() == WType.ARR) {
					Array tagsArray = tagsobj.asArray();
					tags = Arrays.asList(tagsArray.asStringArray());
					// Typically messages from log have these tags
					// TODO make this more elaborate
					if (tags.contains("no_highlight") || tags.contains("notify_none")) {
						continue;
					}
				}

				// Determine if buffer is a privmessage(check localvar "type" for value "private"), and notify for that too
				RelayObject bufferType = b.getLocalVar("type");
				if (bufferType != null && bufferType.asString().equals("private")) {
					// Must have localvar("channel") == prefix
					RelayObject buddyNick = b.getLocalVar("channel");
					if (buddyNick != null && buddyNick.asString().equals(Color.stripColors(hde.getItem("prefix").asString()))) {	
						highlight = true;
					}
				}
				
				// Nothing to do if buffer shouldn't be notified
				// TODO this can be more elaborate
				if (b.getNotifyLevel() == 0)
					continue;
				
				// Nothing to do if not a highlight
				if (!highlight)
					continue;
				
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
}
