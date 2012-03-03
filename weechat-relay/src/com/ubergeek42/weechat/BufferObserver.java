package com.ubergeek42.weechat;

/**
 * Allows objects to receive notifications when certain aspects of a buffer change
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public interface BufferObserver {
	/**
	 * Called when a line is added to a buffer
	 */
	public void onLineAdded();
	
	/**
	 * Called when the buffer is closed in weechat
	 */
	public void onBufferClosed();
}
