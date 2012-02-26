package com.ubergeek42.weechat.weechatrelay.protocol;

public class WInfo extends WObject {
	private String value;
	private String name;

	public WInfo(String name, String value) {
		this.name = name;
		this.value = value;
	}
	
	public String toString() {
		return "[WInfo]:\n  " + name + " -> " + value;
	}
}
