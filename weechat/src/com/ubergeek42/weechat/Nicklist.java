package com.ubergeek42.weechat;

import java.util.ArrayList;

import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.HdataEntry;
import com.ubergeek42.weechat.weechatrelay.protocol.WHdata;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;

public class Nicklist implements WMessageHandler{
	ArrayList<NickItem> nicks = new ArrayList<NickItem>();

	@Override
	public void handleMessage(WMessage m, String id) {
		if (id.equals("_nicklist")) {
			// Which buffer is this for?
			WObject objects[] = m.getObjects();
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				NickItem ni = new NickItem();
				ni.group = hde.getItem("group").asChar();
				ni.visible = hde.getItem("visible").asChar();
				ni.level = hde.getItem("level").asInt();
				ni.name = hde.getItem("name").asString();
				ni.color = hde.getItem("color").asString();
				ni.prefix = hde.getItem("prefix").asString();
				ni.prefixColor = hde.getItem("prefix_color").asString();
				nicks.add(ni);
			}
		}
	}
}
