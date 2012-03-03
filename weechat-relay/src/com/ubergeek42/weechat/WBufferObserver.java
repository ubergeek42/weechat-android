package com.ubergeek42.weechat;

public interface WBufferObserver {
	public void onLineAdded();
	public void onBufferClosed();
}
