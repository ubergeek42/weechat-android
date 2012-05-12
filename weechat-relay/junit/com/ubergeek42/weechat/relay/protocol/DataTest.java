package com.ubergeek42.weechat.relay.protocol;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.junit.Test;

public class DataTest {

	public static byte[] constructWeechatLong(String msg) {
		int length = msg.length();
		
		byte[] data = new byte[length + 1];
		data[0] = (byte) (length&0xFF);
		for(int j=1;j<=length; j++) {
			data[j] = (byte) (msg.charAt(j-1));
		}
		
		return data;
	}
	
	@Test
	public void testGetChar() {
		for (int o = 0; o < 256; o++) {
			Data d = new Data(new byte[] { (byte) (o & 0xFF) });
			char c = d.getChar();
			assertEquals(c, o);
		}
	}

	@Test
	public void testGetByte() {
		for (int o = 0; o < 256; o++) {
			Data d = new Data(new byte[] { (byte) (o & 0xFF) });
			int b = d.getByte();
			assertEquals(b, o);
		}
	}

	@Test
	public void testGetLongInteger() throws UnsupportedEncodingException {
		long testCases[] = new long[] {260L, 0L, -99999999L, 99999999L, 1234567890L, -0L, Long.MAX_VALUE, Long.MIN_VALUE};
		for (int i=0; i<testCases.length; i++) {
			long o = testCases[i];
			String msg = String.format("%d", o);
			
			
			Data d = new Data(DataTest.constructWeechatLong(msg));
			long l = d.getLongInteger();
			assertEquals(l, o);
		}
	}
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetLongIntegerException1() {
		Data d = new Data(new byte[]{5});// Missing data
		d.getLongInteger();
	}
	@Test(expected = RuntimeException.class)
	public void testGetLongIntegerException2() {
		Data d = new Data(new byte[]{0}); // Get long int of length 0
		d.getLongInteger();
	}
	
	@Test
	public void testGetString() {
		String testCases[] = new String[] { "Hello World", "some long message", "another message nothing special about it at all"};
		for(int i=0;i<testCases.length; i++) {
			String o = testCases[i];
			//Data d = new Data(DataTest.constructWeechatLong(o));
			//String s = d.getString();
			//assertEquals(s, o);
		}
	}

}
