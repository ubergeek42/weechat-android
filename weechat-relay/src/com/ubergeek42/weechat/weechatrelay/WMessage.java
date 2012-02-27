package com.ubergeek42.weechat.weechatrelay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.InflaterInputStream;

import com.ubergeek42.weechat.weechatrelay.protocol.WData;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject;
/**
 * Represents a message from the Weechat Relay Server
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class WMessage {
	private ArrayList<WObject> objects = new ArrayList<WObject>();
	private boolean compressed = false;
	private int length = 0;
	private String id = null;

	protected WMessage(byte[] data) {
		WData wd = new WData(data); // Load the data into our consumer

		// Get total message length
		length = wd.getUnsignedInt();

		// Determine compression ratio
		int c = wd.getByte();
		if (c == 0x00) {
			compressed = false;
		} else if (c == 0x01) {
			compressed = true;
			try {
				//System.out.println("[WMessage.constructor] Decompressing data");
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				InflaterInputStream is  = new InflaterInputStream(new ByteArrayInputStream(wd.getByteArray()));
				byte b[] = new byte[256];
				while(is.available()==1) {
					int r = is.read(b);
					if (r<0) break;
					bout.write(b,0,r);
				}
				data = bout.toByteArray();
				wd = new WData(data);
				//System.out.format("[WMessage.constructor] Data size: %d/%d\n", length, data.length+5);// 5 is how much we've already read
			} catch (IOException e) {
				System.err.println("[WMessage.constructor] Failed to decompress data stream");
				e.printStackTrace();
			}
			
		} else {
			throw new RuntimeException("[WMessage.constructor] unknown compression type: " + String.format("%02X",c));
		}
		
		// Optional data element
		id = wd.getString();
		
		// One or more objects at this point
		while(wd.empty()==false) {
			objects.add(wd.getObject());
		}
	}
	/**
	 * Debug message for a WMessage
	 */
	public String toString() {
		String msg = String.format("[WMessage.tostring]\n  Length: %d\n  Compressed: %s\n  ID: %s\n",length, "" + compressed, id);
		for(WObject obj: objects) {
			msg += obj.toString() + "\n";
		}
		return msg;
	}

	/**
	 * @return The ID associated with the message
	 */
	public String getID() {
		return this.id;
	}

	/**
	 * @return The set of objects in the message
	 */
	public WObject[] getObjects() {
		WObject[] ret = new WObject[objects.size()];
		ret = objects.toArray(ret);
		return ret;
	}
}
