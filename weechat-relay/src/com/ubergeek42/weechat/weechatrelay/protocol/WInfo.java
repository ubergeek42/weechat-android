package com.ubergeek42.weechat.weechatrelay.protocol;

/**
 * An Info item, basically a name and value pair
 * See the following URL for more information:
 *  http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_info
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class WInfo extends WObject {
	private String value;
	private String name;

	protected WInfo(String name, String value) {
		this.name = name;
		this.value = value;
	}
	
	/**
	 * @return The name of the Info item
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return The value of the Info item
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * Debug toString
	 */
	public String toString() {
		return "[WInfo]:\n  " + name + " -> " + value;
	}
}
