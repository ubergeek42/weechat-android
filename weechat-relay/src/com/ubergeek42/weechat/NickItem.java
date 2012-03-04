package com.ubergeek42.weechat;

import com.ubergeek42.weechat.relay.protocol.HdataEntry;

public class NickItem {
	private int group;
	private int visible;
	private int level;
	private String name;
	private String color;
	private String prefix;
	private String prefixColor;

	public NickItem(HdataEntry hde) {
		this.group = hde.getItem("group").asChar();
		this.visible = hde.getItem("visible").asChar();
		this.level = hde.getItem("level").asInt();
		this.name = hde.getItem("name").asString();
		this.color = hde.getItem("color").asString();
		this.prefix = hde.getItem("prefix").asString();
		this.prefixColor = hde.getItem("prefix_color").asString();
	}

	public boolean isVisible() {
		return (this.visible == 0x01);
	}

	public boolean isGroup() {
		return (this.group == 0x01);
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", prefix, name);
	}
}
