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
