package com.ubergeek42.relayexample;
import java.util.HashMap;

import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Infolist;
import com.ubergeek42.weechat.relay.protocol.RelayObject;


public class InfolistMessageHandler implements RelayMessageHandler {
	@Override
	public void handleMessage(RelayObject obj, String id) {
		if (id.equals("infolist-test")) {
			if (!(obj instanceof Infolist)) {
				System.err.println("Error: unexpected object type");
			}
			Infolist infolist = (Infolist) obj;
			System.out.println("[Infolist] " + infolist.getName());
			for(int i=0;i<infolist.size(); i++) {
				System.out.format("  Item %d\n",i);
				HashMap<String,RelayObject> item = infolist.getItem(i);
				for(String key: item.keySet()) {
					System.out.format("    %s: %s\n",key, item.get(key));
				}
			}
		} else {
			System.err.println("Unexpected message ID");
		}
	}

}
