package com.ubergeek42.weechat.weechatrelay;

/**
 * Provides notifications about the connection with the server such as when a
 * connection is made or lost.
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public interface WRelayConnectionHandler {
	/**
	 * Called when a connection to the server is established, and commands can
	 * begin to be sent/received.
	 */
	public void onConnect();

	/**
	 * Called when the server is disconnected, either through error, timeout, or
	 * because the client requested a disconnect.
	 */
	public void onDisconnect();
}
