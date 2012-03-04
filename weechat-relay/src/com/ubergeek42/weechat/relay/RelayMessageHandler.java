package com.ubergeek42.weechat.relay;

import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * An interface for receiving message callbacks from WRelayConnection
 * @author ubergeek42<kj@ubergeek42.com>
 */
public interface RelayMessageHandler {
	/**
	 * Called each time a message is received
	 * @param obj - A relay protocol object from the message
	 * @param id - The id that triggered this callback
	 */
	public abstract void handleMessage(RelayObject obj, String id);
}
