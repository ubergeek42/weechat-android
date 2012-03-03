package com.ubergeek42.weechat.handler;
import java.util.ArrayList;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Manages the list of Buffers in weechat
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class BufferManager implements RelayMessageHandler {

	ArrayList<Buffer> buffers = new ArrayList<Buffer>();
	private BufferManagerObserver onChanged;
	
	public Buffer findByPointer(String pointer) {
		for(Buffer b: buffers) {
			if (pointer.equals(b.getPointer()))
				return b;
		}
		return null;
	}
	
	@Override
	public void handleMessage(RelayMessage m, String id) {
		RelayObject objects[] = m.getObjects();
		if (id.equals("listbuffers")) {
			if (objects.length != 1) {
				System.out.println("[ListBufferHandler.handleMessage] Expected 1 object, got " + objects.length);
			}
			if (!(objects[0] instanceof Hdata)) {
				System.out.println("[ListBufferHandler.handleMessage] Expected WHData, got " + objects[0].getClass());
				return;
			}
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				
				Buffer wb = new Buffer();
				wb.setPointer(hde.getPointer());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				wb.setShortName(hde.getItem("short_name").asString());
				wb.setTitle(hde.getItem("title").asString());
				wb.setNicklistVisible(hde.getItem("nicklist").asInt()==1);
				wb.setType(hde.getItem("type").asInt());
				
				buffers.add(wb);
			}
		} else if (id.equals("_buffer_opened")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				
				Buffer wb = new Buffer();
				wb.setPointer(hde.getPointer());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				wb.setShortName(hde.getItem("short_name").asString());
				wb.setTitle(hde.getItem("title").asString());
				wb.setNicklistVisible(hde.getItem("nicklist").asInt()==1);
				
				// also get "prev_buffer", "next_buffer", and "local_variables"
				// Don't get "type" though
				
				buffers.add(wb);
			}
		} else if(id.equals("_buffer_type_changed")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				Buffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.println("[ChatBuffers:_buffer_type_changed] Unable to find buffer to update type");
					return;
				}
				wb.setType(hde.getItem("type").asInt());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			}
		} else if(id.equals("_buffer_moved") || id.equals("_buffer_merged") || id.equals("_buffer_unmerged")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				Buffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.format("[ChatBuffers:%s] Unable to find buffer to update\n",id);
					return;
				}
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			}
		} else if (id.equals("_buffer_renamed") || id.equals("_buffer_title_changed")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				Buffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.format("[ChatBuffers:%s] Unable to find buffer to update\n",id);
					return;
				}
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				if(id.equals("_buffer_title_changed")) {
					wb.setTitle(hde.getItem("title").asString());
				} else {
					wb.setShortName(hde.getItem("short_name").asString());
					// TODO: Manipulate local variables
				}
			}
		} else if (id.equals("_buffer_localvar_added") || id.equals("_buffer_localvar_changed") || id.equals("_buffer_localvar_removed")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				Buffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.format("[ChatBuffers:%s] Unable to find buffer to update\n",id);
					return;
				}
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				// TODO: Manipulate local variables
			}
		} else if (id.equals("_buffer_closing")) {
			Hdata whdata = (Hdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				for(int j=0;j<buffers.size();j++) {
					if (buffers.get(j).getPointer().equals(hde.getPointer())) {
						Buffer wb = buffers.get(j);
						wb.destroy();
						buffers.remove(j);
						break;
					}
				}
			}
		} else {
			System.out.println("[ChatBuffers] Unknown ID to handle: " + id);
		}
		this.onChanged.onBuffersChanged();
	}

	public Buffer getBuffer(int index) {
		return buffers.get(index);
	}
	
	public int getNumBuffers() {
		return buffers.size();
	}

	public void onChanged(BufferManagerObserver bo) {
		this.onChanged = bo;
	}

}
