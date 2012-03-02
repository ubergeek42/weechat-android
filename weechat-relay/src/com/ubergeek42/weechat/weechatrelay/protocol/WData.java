package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.HashMap;

import com.ubergeek42.weechat.Helper;
import com.ubergeek42.weechat.weechatrelay.protocol.WObject.WType;


/**
 * Used internally to construct WObjects and parse the binary messages from the Relay Server
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class WData {
	
	private byte[] data;
	private int pointer; // Current location in the byte array
	public WData(byte[] data) {
		this.data = data;
		this.pointer = 0;
	}
	
	public int getUnsignedInt() {
		if (pointer+4 > data.length) {
			throw new IndexOutOfBoundsException("[WData.getUnsignedInt] Not enough data to compute length");
		}
		int ret = ((data[pointer+0] & 0xFF) << 24) |
				  ((data[pointer+1] & 0xFF) << 16) |
				  ((data[pointer+2] & 0xFF) <<  8) |
				  ((data[pointer+3] & 0xFF));
		
		pointer += 4;
		return ret;
	}
	
	public int getByte() {
		int ret = (int)(data[pointer] & 0xFF);
		
		pointer++;
		return ret;
	}
	public char getChar() {
		return (char)getByte();
	}
	
	// Might have to change to a BigInteger...
	public long getLongInteger() {
		int length = getByte();
		if (pointer+length > data.length) {
			throw new IndexOutOfBoundsException("[WData.getLongInteger] Not enough data");
		}
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<length;i++) {
			sb.append(getChar());
		}

		return Long.parseLong(sb.toString());
	}
	
	public String getString() {
		int length = getUnsignedInt();
		if (pointer+length > data.length) {
			throw new IndexOutOfBoundsException("[WData.getString] Not enough data");
		}
		if (length ==  0) return "";
		if (length == -1) return null;
		
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<length;i++) {
			sb.append(getChar());
		}

		return sb.toString();
	}

	// XXX: untested
	public byte[] getBuffer() {
		int length = getUnsignedInt();
		if (pointer+length > data.length) {
			throw new IndexOutOfBoundsException("[WData.getBuffer] Not enough data");
		}
		if (length == 0) {
			return null;
		}
		
		byte[] ret = Helper.copyOfRange(data, pointer, pointer+length);
		
		pointer += length;
		return ret;
	}
	
	public String getPointer() {
		int length = getByte();
		if (pointer+length > data.length) {
			throw new IndexOutOfBoundsException("[WData.getPointer] Not enough data");
		}

		StringBuilder sb = new StringBuilder();
		for(int i=0;i<length;i++) {
			sb.append(getChar());
		}
		
		if (Integer.parseInt(sb.toString(),16) == 0) {
			// Null Pointer
		}
		
		return "0x" + sb.toString();
	}
	
	// Maybe return a reasonable "Date" object or similar
	public long getTime() {
		long time = getLongInteger();
		return time;
	}
	
	public WHashtable getHashtable() {
		WType keyType = getType();
		WType valueType = getType();
		int count = getUnsignedInt();
		
		WHashtable hta = new WHashtable(keyType, valueType);
		for(int i=0; i<count; i++) {
			WObject k = getObject(keyType);
			WObject v = getObject(valueType);
			hta.put(k, v);
		}
		
		return hta;
	}
	
	public WHdata getHdata() {
		WHdata whd = new WHdata();
		
		String hpath = getString();
		String keys = getString();
		int count = getUnsignedInt();
		
		whd.path_list = hpath.split("/");
		whd.setKeys(keys.split(","));
		
		for (int i=0;i<count;i++) {
			HdataEntry hde = new HdataEntry();
			for (int j=0; j<whd.path_list.length; j++) {
				String pointer = getPointer();
				hde.addPointer(pointer);
			}
			
			for (int j=0; j<whd.key_list.length; j++) {
				hde.addObject(whd.key_list[j], getObject(whd.type_list[j]));
			}
			whd.addItem(hde);
		}
		return whd;
	}
	
	public WInfo getInfo() {
		String name  = getString();
		String value = getString();
		return new WInfo(name, value);
	}
	
	public WInfolist getInfolist() {
		String name = getString();
		int count = getUnsignedInt();
		
		WInfolist wil = new WInfolist(name);
		
		for(int i=0;i<count;i++) {
			int numItems = getUnsignedInt();
			HashMap<String,WObject> variables = new HashMap<String,WObject>();
			for(int j=0;j<numItems;j++) {
				String itemName = getString();
				WType itemType = getType();
				WObject item = getObject(itemType);
				variables.put(itemName, item);
			}
			wil.addItem(variables);
		}
		
		return wil;
	}
	
	private WType getType() {
		char a = getChar();
		char b = getChar();
		char c = getChar();
		WType type = WType.valueOf(new String(""+a+b+c).toUpperCase());
		return type;
	}
	public WObject getObject() {
		WType type = getType();
		return getObject(type);
	}
	
	private WObject getObject(WType type) {
		WObject ret = null;
		
		switch (type) {
		case CHR:
			ret = new WObject(getChar()); break;
		case INT:
			ret = new WObject(getUnsignedInt()); break;
		case LON:
			ret = new WObject(getLongInteger()); break;
		case STR:
			ret = new WObject(getString()); break;
		case BUF:
			ret = new WObject(getBuffer()); break;
		case PTR:
			ret = new WObject(getPointer()); break;
		case TIM:
			ret = new WObject(getTime()); break;
		case HTB:
			ret = getHashtable(); break;
		case HDA:
			ret = getHdata(); break;
		case INF:
			ret = getInfo(); break;
		case INL:
			ret = getInfolist(); break;
		default:
			System.err.println("[WData.getObject] Unknown object type: " + type);
		}
		// Set the type of the object
		if (ret != null)
			ret.setType(type);
		return ret;
	}
	
	// Returns the unconsumed portion of the data stream
	public byte[] getByteArray() {
		return Helper.copyOfRange(data, pointer, data.length);
	}

	public boolean empty() {
		return pointer==data.length;
	}
}
