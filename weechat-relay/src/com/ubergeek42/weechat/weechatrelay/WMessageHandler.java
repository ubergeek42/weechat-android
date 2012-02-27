package com.ubergeek42.weechat.weechatrelay;

/**
 * An interface for receiving message callbacks from WRelayConnection
 * @author ubergeek42<kj@ubergeek42.com>
 */
public interface WMessageHandler {
	/**
	 * Called each time a message is received
	 * @param msg - The message received
	 * @param id - The id that triggered this callback
	 */
	public abstract void handleMessage(WMessage msg, String id);
}
