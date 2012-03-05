package com.ubergeek42.relayexample;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Info;
import com.ubergeek42.weechat.relay.protocol.RelayObject;


public class InfoMessageHandler implements RelayMessageHandler {
	
	@Override
	public void handleMessage(RelayObject obj, String id) {
		if (id.equals("info-test")) {
			if (!(obj instanceof Info)) {
				System.err.println("Error: unexpected object type");
			}
			Info info = (Info) obj;
			System.out.printf("[Info] %s = %s\n", info.getName(), info.getValue());
		} else {
			System.err.println("Unexpected message ID");
		}
	}

}
