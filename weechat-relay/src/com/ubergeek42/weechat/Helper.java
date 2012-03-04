package com.ubergeek42.weechat;
/**
 * Provides some helper functions that are required by the library
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class Helper {
	// Basically Arrays.copyOfRange, included as android 2.1 is missing this
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
