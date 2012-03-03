package com.ubergeek42.weechat;

import java.util.Date;
import java.util.HashSet;

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
		System.err.println("handleMessage Called");
		WObject objects[] = m.getObjects();
		if (id.equals("_buffer_line_added")) {
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				// TODO: check last item of path is line_data
				
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
		} else if (id.equals("listlines")) {
			long start = System.currentTimeMillis();
			System.err.println("Listlines started");
			WHdata whdata = (WHdata) objects[0];
			
			HashSet<WeechatBuffer> toUpdate = new HashSet<WeechatBuffer>();
			for(int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				// TODO: check last item of path is line_data
				
				
				// Get the information about the "line"
				String message = hde.getItem("message").asString();
				String prefix = hde.getItem("prefix").asString();
				boolean displayed = (hde.getItem("displayed").asChar()==0x01);
				Date time = hde.getItem("date").asTime();
				String bPointer = hde.getPointer(0);
	
				// Find the buffer to put the line in
				WeechatBuffer buffer = cb.findByPointer(bPointer);
				
				// Create a new message object, and add it to the correct buffer
				ChatMessage cm = new ChatMessage();
				cm.setPrefix(prefix);
				cm.setMessage(message);
				cm.setTimestamp(time);
				cm.setVisible(displayed);
				
				// XXX: debugging statement
				System.out.println(buffer.getFullName() + " " + cm);
				
				// TODO: check buffer isn't null...
				buffer.addLineNoNotify(cm);
				toUpdate.add(buffer);
			}
			for(WeechatBuffer wb: toUpdate) {
				wb.notifyObservers();
			}
			long elapsed = System.currentTimeMillis() - start;
			System.err.println("Listlines finished: " + elapsed);
		} else {
			// Unhandled ID message...
		}
	}
	
	
}
