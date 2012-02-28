package com.ubergeek42.relayexample;
import java.util.HashMap;

import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.WInfolist;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;


public class InfolistMessageHandler implements WMessageHandler {
	@Override
	public void handleMessage(WMessage msg, String id) {
		WObject[] objs = msg.getObjects();
		System.out.println("Number of objects in message: " + objs.length);
		if (id.equals("infolist-test")) {
			for(WObject o: objs) {
				if (!(o instanceof WInfolist)) {
					System.err.println("Error: unexpected object type");
				}
				WInfolist infolist = (WInfolist) o;
				System.out.println("[Infolist] " + infolist.getName());
				for(int i=0;i<infolist.size(); i++) {
					System.out.format("  Item %d\n",i);
					HashMap<String,WObject> item = infolist.getItem(i);
					for(String key: item.keySet()) {
						System.out.format("    %s: %s\n",key, item.get(key));
					}
				}
				
			}
		} else {
			System.err.println("Unexpected message ID");
		}
	}

}
