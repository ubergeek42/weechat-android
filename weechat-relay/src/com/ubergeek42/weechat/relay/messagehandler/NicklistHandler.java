package com.ubergeek42.weechat.relay.messagehandler;

import java.util.ArrayList;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.NickItem;
import com.ubergeek42.weechat.relay.RelayMessage;
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

	ArrayList<NickItem> nicks = new ArrayList<NickItem>();
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
