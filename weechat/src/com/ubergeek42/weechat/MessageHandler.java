package com.ubergeek42.weechat;

import java.util.Date;
import java.util.HashMap;

import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.HdataEntry;
import com.ubergeek42.weechat.weechatrelay.protocol.WHdata;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;

public class MessageHandler implements WMessageHandler {

	private ChatBuffers cb;
	
	public MessageHandler(ChatBuffers cb) {
		this.cb = cb;
	}
	
	@Override
	public void handleMessage(WMessage m, String id) {
		WObject objects[] = m.getObjects();
		WHdata whdata = (WHdata) objects[0];
		for (int i=0; i<whdata.getCount(); i++) {
			HdataEntry hde = whdata.getItem(i);
			
			// Get the information about the "line"
			String message = hde.getItem("message").asString();
			String prefix = hde.getItem("prefix").asString();
			boolean displayed = (hde.getItem("displayed").asChar()==0x01);
			Date time = hde.getItem("date").asTime();
			String bPointer = hde.getItem("buffer").asPointer();

			// Find the buffer to put the line in
			WeechatBuffer buffer = cb.findByPointer(bPointer);
			
			// Create a new message object, and add it to the correct buffer
			ChatMessage cm = new ChatMessage();
			cm.setPrefix(prefix);
			cm.setMessage(message);
			cm.setTimestamp(time);
			cm.setVisible(displayed);
			
			// XXX: debugging statement
			System.out.println(cm);
			
			buffer.addLine(cm);
		}
	}
	
	
}
