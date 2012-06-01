package com.ubergeek42.weechat.relay.messagehandler;

import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class UpgradeHandler implements RelayMessageHandler {

	private UpgradeObserver uo;
	public UpgradeHandler(UpgradeObserver uo) {
		this.uo = uo;
	}
	
	@Override
	public void handleMessage(RelayObject obj, String id) {
		System.out.println("Got id: " + id);
		if (id.equals("_upgrade_ended") || id.equals("_upgrade")) {
			uo.onUpgrade();
		}
	}

}
