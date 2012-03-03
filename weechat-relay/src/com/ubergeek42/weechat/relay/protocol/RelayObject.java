package com.ubergeek42.weechat.relay.protocol;

import java.util.Date;

/**
 * An object contained in a Weechat Relay Message
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayObject {
	public enum WType {
		CHR, INT, LON, STR, BUF, PTR, TIM, HTB, HDA, INF, INL, UNKNOWN
	}

	private char charValue;
	private int intValue;
	private long longValue;
	private String strValue;
	private byte[] baValue;

	protected WType type = WType.UNKNOWN;

	protected RelayObject() {
		// Does nothing
	}

	protected RelayObject(char c) {
		charValue = c;
	}

	protected RelayObject(int i) {
		intValue = i;
	}

	protected RelayObject(long l) {
		longValue = l;
	}

	protected RelayObject(String s) {
		strValue = s;
	}

	protected RelayObject(byte[] b) {
		baValue = b;
	}

	protected void setType(WType t) {
		type = t;
	}

	/**
	 * Throws an exception if the object type doesn't match the argument
	 * 
	 * @param t
	 *            - the wype we expect/want from this Object
	 */
	private void checkType(WType t) {
		if (type != t)
			throw new RuntimeException("Cannont convert from " + type + " to "
					+ t);
	}

	/**
	 * @return The char representation of an object
	 */
	public char asChar() {
		checkType(WType.CHR);
		return charValue;
	}

	/**
	 * @return The unsigned integer representation of the object
	 */
	public int asInt() {
		checkType(WType.INT);
		return intValue;
	}

	/**
	 * @return The long integer representation of the object TODO: see if this
	 *         needs to be changed to a BigInteger
	 */
	public long asLong() {
		checkType(WType.LON);
		return longValue;
	}

	/**
	 * @return The string representation of the object
	 */
	public String asString() {
		checkType(WType.STR);
		return strValue;
	}

	/**
	 * @return A byte array representation of the object
	 */
	public byte[] asBytes() {
		checkType(WType.BUF);
		return baValue;
	}

	/**
	 * @return A string representing a pointer(e.g. 0xDEADBEEF)
	 */
	public String asPointer() {
		checkType(WType.PTR);
		return strValue;
	}

	/**
	 * @return A Date representation of the object
	 */
	public Date asTime() {
		checkType(WType.TIM);
		return new Date(longValue * 1000);
	}

	/**
	 * Debug string representation of the object
	 */
	public String toString() {
		String value = "Unknown";
		switch (type) {
		case CHR:
			value = String.format("0x%02x", (int) asChar());
			break;
		case INT:
			value = "" + asInt();
			break;
		case LON:
			value = "" + asLong();
			break;
		case STR:
			value = '"' + asString() + '"';
			break;
		case TIM:
			value = "" + asTime();
			break;
		case PTR:
			value = "" + asPointer();
			break;
		case BUF:
			value = "" + asBytes();
			break; // Need a better printer for a byte buffer
		}
		// return String.format("%s -> %s", type, value);
		return String.format("%s", value);
	}
}
