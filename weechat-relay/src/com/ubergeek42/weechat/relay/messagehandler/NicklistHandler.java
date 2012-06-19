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

import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.NickItem;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Handles messages that relate to nicklist
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class NicklistHandler implements RelayMessageHandler {
	private static Logger logger = LoggerFactory.getLogger(NicklistHandler.class);
	private BufferManager cbs;

	public NicklistHandler(BufferManager cbs) {
		this.cbs = cbs;
	}

	@Override
	public void handleMessage(RelayObject obj, String id) {
		if (id.equals("_nicklist") || id.equals("nicklist")) {
			// TODO: verify path is nicklist_item, that obj is Hdata

			HashSet<Buffer> nicklistCleared = new HashSet<Buffer>();
			
			// Which buffer is this for?
			Hdata whdata = (Hdata) obj;
			for (int i = 0; i < whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				NickItem ni = new NickItem(hde);
				
				// Not a nick we care about(its a group or invisible)
				if (ni.isGroup() || !ni.isVisible())
					continue;
				
				Buffer wb = cbs.findByPointer(hde.getPointer(0));
				if (!nicklistCleared.contains(wb)) {
					nicklistCleared.add(wb);
					wb.clearNicklist();
				}
				wb.addNick(ni);
			}
		}
	}
}
