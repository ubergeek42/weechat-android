package com.ubergeek42.relayexample;
import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.WInfo;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;


public class InfoMessageHandler implements WMessageHandler {
	
	@Override
	public void handleMessage(WMessage msg, String id) {
		WObject[] objs = msg.getObjects();
		System.out.println("Number of objects in message: " + objs.length);
		if (id.equals("info-test")) {
			for(WObject o: objs) {
				if (!(o instanceof WInfo)) {
					System.err.println("Error: unexpected object type");
				}
				WInfo info = (WInfo) o;
				System.out.printf("[Info] %s = %s\n", info.getName(), info.getValue());
			}
		} else {
			System.err.println("Unexpected message ID");
		}
	}

}
