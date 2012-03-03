package com.ubergeek42.weechat;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.HdataEntry;
import com.ubergeek42.weechat.weechatrelay.protocol.WHdata;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;

public class Nicklist implements WMessageHandler {
	private static Logger logger = LoggerFactory.getLogger(Nicklist.class);

	ArrayList<NickItem> nicks = new ArrayList<NickItem>();
	private ChatBuffers cbs;

	public Nicklist(ChatBuffers cbs) {
		this.cbs = cbs;
	}

	@Override
	public void handleMessage(WMessage m, String id) {
		if (id.equals("_nicklist") || id.equals("nicklist")) {
			// TODO: verify path is nicklist_item

			// Which buffer is this for?
			WObject objects[] = m.getObjects();
			WHdata whdata = (WHdata) objects[0];
			for (int i = 0; i < whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				NickItem ni = new NickItem();
				ni.group = hde.getItem("group").asChar();
				ni.visible = hde.getItem("visible").asChar();
				ni.level = hde.getItem("level").asInt();
				ni.name = hde.getItem("name").asString();
				ni.color = hde.getItem("color").asString();
				ni.prefix = hde.getItem("prefix").asString();
				ni.prefixColor = hde.getItem("prefix_color").asString();

				
				
				// Not a nick we care about(its a group or invisible)
				if (ni.group == 0x01 || ni.visible != 0x01)
					continue;
				
				logger.info(ni.toString());
				
				WeechatBuffer wb = cbs.findByPointer(hde.getPointer(0));
				wb.addNick(ni);
			}
		}
	}
}
