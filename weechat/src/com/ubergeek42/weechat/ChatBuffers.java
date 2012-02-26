package com.ubergeek42.weechat;
import java.util.ArrayList;

import com.ubergeek42.weechat.weechatrelay.WMessage;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.protocol.HdataEntry;
import com.ubergeek42.weechat.weechatrelay.protocol.WHdata;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;


public class ChatBuffers implements WMessageHandler {

	ArrayList<WeechatBuffer> buffers = new ArrayList<WeechatBuffer>();
	private ChatBufferObserver onChanged;
	
	public WeechatBuffer findByPointer(String pointer) {
		for(WeechatBuffer b: buffers) {
			if (pointer.equals(b.getPointer()))
				return b;
		}
		return null;
	}
	
	@Override
	public void handleMessage(WMessage m, String id) {
		WObject objects[] = m.getObjects();
		if (id.equals("listbuffers")) {
			if (objects.length != 1) {
				System.out.println("[ListBufferHandler.handleMessage] Expected 1 object, got " + objects.length);
			}
			if (!(objects[0] instanceof WHdata)) {
				System.out.println("[ListBufferHandler.handleMessage] Expected WHData, got " + objects[0].getClass());
				return;
			}
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				
				WeechatBuffer wb = new WeechatBuffer();
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
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				
				WeechatBuffer wb = new WeechatBuffer();
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
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				WeechatBuffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.println("[ChatBuffers:_buffer_type_changed] Unable to find buffer to update type");
					return;
				}
				wb.setType(hde.getItem("type").asInt());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			}
		} else if(id.equals("_buffer_moved") || id.equals("_buffer_merged") || id.equals("_buffer_unmerged")) {
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				WeechatBuffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.format("[ChatBuffers:%s] Unable to find buffer to update\n",id);
					return;
				}
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			}
		} else if (id.equals("_buffer_renamed") || id.equals("_buffer_title_changed")) {
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				WeechatBuffer wb = findByPointer(hde.getPointer());
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
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				WeechatBuffer wb = findByPointer(hde.getPointer());
				if (wb==null) {
					System.err.format("[ChatBuffers:%s] Unable to find buffer to update\n",id);
					return;
				}
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				// TODO: Manipulate local variables
			}
		} else if (id.equals("_buffer_closing")) {
			WHdata whdata = (WHdata) objects[0];
			for (int i=0; i<whdata.getCount(); i++) {
				HdataEntry hde = whdata.getItem(i);
				for(int j=0;j<buffers.size();j++) {
					if (buffers.get(j).getPointer().equals(hde.getPointer())) {
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

	public WeechatBuffer getBuffer(int index) {
		return buffers.get(index);
	}
	
	public int getNumBuffers() {
		return buffers.size();
	}

	public void onChanged(ChatBufferObserver bo) {
		this.onChanged = bo;
	}

}
