package com.ubergeek42.weechat.relay.messagehandler;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Manages a list of buffers present in weechat
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class BufferManager implements RelayMessageHandler {
	private static Logger logger = LoggerFactory.getLogger(BufferManager.class);
	
	ArrayList<Buffer> buffers = new ArrayList<Buffer>();
	private BufferManagerObserver onChangeObserver;
	
	/**
	 * Locate and returns a Buffer object based on it's pointer
	 * @param pointer - Pointer to a weechat buffer(e.g. 0xDEADBEEF)
	 * @return Buffer object for the associated buffer, or null if not found
	 */
	public Buffer findByPointer(String pointer) {
		for(Buffer b: buffers) {
			if (pointer.equals(b.getPointer()))
				return b;
		}
		return null;
	}
	/**
	 * Locate and returns a Buffer object based on it's name
	 * @param pointer - Name of a weechat buffer(e.g. irc.freenode.#weechat)
	 * @return Buffer object for the associated buffer, or null if not found
	 */
	public Buffer findByName(String bufferName) {
		for(Buffer b: buffers) {
			if (bufferName.equals(b.getFullName()))
				return b;
		}
		return null;
	}
	/**
	 * Get the Buffer at the specified index
	 * @param index - The index to retrieve
	 * @return Buffer object at the given index
	 */
	public Buffer getBuffer(int index) {
		return buffers.get(index);
	}
	
	/**
	 * Gets the number of buffers this manager knows about
	 * @return Number of buffers
	 */
	public int getNumBuffers() {
		return buffers.size();
	}

	/**
	 * Register a single observer to be notified when the list of buffers changes
	 * @param bo - The observer to receive notifications
	 */
	public void onChanged(BufferManagerObserver bo) {
		this.onChangeObserver = bo;
	}
	/**
	 * Can be called to inform clients that the buffers have changed in some way
	 * Currently used for notifying about unread lines/highlights
	 */
	public void buffersChanged() {
		if (onChangeObserver != null) {
			onChangeObserver.onBuffersChanged();
		}
	}
	
	@Override
	public void handleMessage(RelayObject obj, String id) {
		if (!(obj instanceof Hdata)) {
			logger.debug("Expected hdata, got " + obj.getClass());
			return;
		}
		Hdata whdata = (Hdata) obj;
		
		for (int i=0; i<whdata.getCount(); i++) {
			HdataEntry hde = whdata.getItem(i);
			Buffer wb = new Buffer();
			if (id.equals("listbuffers")) {
				wb.setPointer(hde.getPointer());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				wb.setShortName(hde.getItem("short_name").asString());
				wb.setTitle(hde.getItem("title").asString());
				wb.setNicklistVisible(hde.getItem("nicklist").asInt()==1);
				wb.setType(hde.getItem("type").asInt());
				
				Hashtable ht = (Hashtable)hde.getItem("local_variables");
				if (ht!=null)
					wb.setLocals(ht);
				
				buffers.add(wb);
			} else if (id.equals("_buffer_opened")) {
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
			wb = findByPointer(hde.getPointer(0));
			if (wb==null) {
				logger.debug("Unable to find buffer to update");
				return;
			}
			if(id.equals("_buffer_type_changed")) {
				wb.setType(hde.getItem("type").asInt());
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			} else if(id.equals("_buffer_moved") || id.equals("_buffer_merged") || id.equals("_buffer_unmerged")) {
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
			} else if (id.equals("_buffer_renamed") || id.equals("_buffer_title_changed")) {
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());
				if(id.equals("_buffer_title_changed")) {
					wb.setTitle(hde.getItem("title").asString());
				} else {
					wb.setShortName(hde.getItem("short_name").asString());
					Hashtable ht = (Hashtable)hde.getItem("local_variables");
					if (ht!=null)
						wb.setLocals(ht);
				}
			} else if (id.equals("_buffer_localvar_added") || id.equals("_buffer_localvar_changed") || id.equals("_buffer_localvar_removed")) {
				wb.setNumber(hde.getItem("number").asInt());
				wb.setFullName(hde.getItem("full_name").asString());

				Hashtable ht = (Hashtable)hde.getItem("local_variables");
				if (ht!=null)
					wb.setLocals(ht);
			} else if (id.equals("_buffer_closing")) {
				for(int j=0;j<buffers.size();j++) {
					if (buffers.get(j).getPointer().equals(hde.getPointer())) {
						Buffer b = buffers.remove(j);
						b.destroy();
						break;
					}
				}
			} else {
				logger.debug("Unknown message ID: \"" + id+"\"");
			}
		}
		buffersChanged();
	}
	

	

}
