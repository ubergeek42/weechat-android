package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.Date;

public class WObject {
	public enum WType {
		CHR,
		INT,
		LON,
		STR,
		BUF,
		PTR,
		TIM,
		HTB,
		HDA,
		INF,
		INL,
		UNKNOWN
	}

	private char charValue;
	private int intValue;
	private long longValue;
	private String strValue;
	private byte[] baValue;
	
	protected WType type = WType.UNKNOWN;
	
	public WObject() {
		// Does nothing
	}
	
	public WObject(char c)       { charValue = c; }
	public WObject(int i)        { intValue = i; }
	public WObject(long l)       { longValue = l; }
	public WObject(String s)     { strValue = s; }
	public WObject(byte[] b)     { baValue = b; }
	public void setType(WType t) { type = t; }
	
	public char   asChar()    { checkType(WType.CHR); return charValue; }
	public int    asInt()     { checkType(WType.INT); return intValue;  }
	public long   asLong()    { checkType(WType.LON); return longValue; }
	public String asString()  { checkType(WType.STR); return strValue;  }
	public byte[] asBytes()   { checkType(WType.BUF); return baValue;   }
	public String asPointer() { checkType(WType.PTR); return strValue;  }
	public Date asTime()      {
		checkType(WType.TIM);
		return new Date(longValue*1000);
	}
	
	private void checkType(WType t) {
		if (type != t) throw new RuntimeException("Cannont convert from " + type + " to " + t);
	}
	
	public String toString() {
		String value="Unknown";
		switch (type) {
		case CHR: value = String.format("0x%02x",(int)asChar()); break;
		case INT: value = "" + asInt(); break;
		case LON: value = "" + asLong(); break;
		case STR: value = '"' + asString()+'"'; break;
		case TIM: value = "" + asTime(); break;
		case PTR: value = "" + asPointer(); break;
		case BUF: value = "" + asBytes(); break; // Need a better printer for a byte buffer
		}
		//return String.format("%s -> %s", type, value);
		return String.format("%s", value);
	}
}
