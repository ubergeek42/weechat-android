package com.ubergeek42.weechat.relay.messagehandler;

import java.util.Date;
import java.util.HashSet;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class LineHandler implements RelayMessageHandler {

	private BufferManager cb;
	
	public LineHandler(BufferManager cb) {
		this.cb = cb;
	}
	
	@Override
	public void handleMessage(RelayObject obj, String id) {
		Hdata whdata = (Hdata) obj;
		if (id.equals("_buffer_line_added")) {
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				// TODO: check last item of path is line_data

				// Get the information about the "line"
				String message = hde.getItem("message").asString();
				String prefix = hde.getItem("prefix").asString();
				boolean displayed = (hde.getItem("displayed").asChar()==0x01);
				Date time = hde.getItem("date").asTime();
				String bPointer = hde.getItem("buffer").asPointer();
				
				// Try to get highlight status(added in 0.3.8-dev: 2012-03-06)
				RelayObject t = hde.getItem("highlight");
				boolean highlight = false;
				if(t!=null) highlight = (t.asChar()==0x01);
	
				// Find the buffer to put the line in
				Buffer buffer = cb.findByPointer(bPointer);
				
				// Create a new message object, and add it to the correct buffer
				BufferLine cm = new BufferLine();
				cm.setPrefix(prefix);
				cm.setMessage(message);
				cm.setTimestamp(time);
				cm.setVisible(displayed);
				cm.setHighlight(highlight);
				
				// XXX: debugging statement
				System.out.println(cm);
				
				buffer.addLine(cm);
			}
		} else if (id.equals("listlines_reverse")) { // lines come in most recent to least recent
			HashSet<Buffer> toUpdate = new HashSet<Buffer>();
			for(int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				// TODO: check last item of path is line_data
				
				// Get the information about the "line"
				String message = hde.getItem("message").asString();
				String prefix = hde.getItem("prefix").asString();
				boolean displayed = (hde.getItem("displayed").asChar()==0x01);
				Date time = hde.getItem("date").asTime();
				String bPointer = hde.getPointer(0);
				boolean highlight = (hde.getItem("highlight").asChar()==0x01);
	
				// Find the buffer to put the line in
				Buffer buffer = cb.findByPointer(bPointer);
				
				// Create a new message object, and add it to the correct buffer
				BufferLine cm = new BufferLine();
				cm.setPrefix(prefix);
				cm.setMessage(message);
				cm.setTimestamp(time);
				cm.setVisible(displayed);

				cm.setHighlight(highlight);
				
				// XXX: debugging statement
				//System.out.println(buffer.getFullName() + " " + cm);
				
				// TODO: check buffer isn't null...
				buffer.addLineFirstNoNotify(cm);
				toUpdate.add(buffer);
			}
			for(Buffer wb: toUpdate) {
				wb.notifyObservers();
			}
		} else {
			// Unhandled ID message...
		}
	}
	
	
}
