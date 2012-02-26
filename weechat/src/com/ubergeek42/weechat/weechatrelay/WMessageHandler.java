package com.ubergeek42.weechat.weechatrelay;

public interface WMessageHandler {
	public abstract void handleMessage(WMessage m, String id);
}
