package com.ubergeek42.weechat;

public class Helper {
	// See http://stackoverflow.com/a/7970678 (taken from the OpenJDK source)
	public static byte[] copyOfRange(byte[] original, int from, int to) {
	    int newLength = to - from;
	    if (newLength < 0)
	        throw new IllegalArgumentException(from + " > " + to);
	    byte[] copy = new byte[newLength];
	    System.arraycopy(original, from, copy, 0,
	                     Math.min(original.length - from, newLength));
	    return copy;
	}
}
