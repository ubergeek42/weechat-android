package com.ubergeek42.weechat.handler;

/**
 * Observer to receive notifications when buffers change
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public interface BufferManagerObserver {
	/**
	 * Called whenever a buffer changes(or is added/removed)
	 */
	public void onBuffersChanged();
}
