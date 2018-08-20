package com.ubergeek42.weechat.relay.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

public class DataTest {

	public static byte[] constructWeechatLong(long l) {
		String msg = String.format("%d", l);
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
			assertEquals(o, c);
		}
	}

	@Test
	public void testGetByte() {
		for (int o = 0; o < 256; o++) {
			Data d = new Data(new byte[] { (byte) (o & 0xFF) });
			int b = d.getByte();
			assertEquals(o, b);
		}
	}
	
	@Test
	public void testGetInteger() {
		int testCases[] = new int[] {260, 0, -99999999, 99999999, 1234567890, -0, Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (int o: testCases) {
			ByteBuffer bb = ByteBuffer.allocate(6);
			bb.order(ByteOrder.BIG_ENDIAN);
			bb.putInt(o);
			Data d = new Data(bb.array());
			
			int i = d.getUnsignedInt();
			assertEquals(o, i);
		}
	}

	@Test
	public void testGetLongInteger() throws UnsupportedEncodingException {
		long testCases[] = new long[] {260L, 0L, -99999999L, 99999999L, 1234567890L, -0L, Long.MAX_VALUE, Long.MIN_VALUE};
		for (int i=0; i<testCases.length; i++) {
			long o = testCases[i];
			Data d = new Data(DataTest.constructWeechatLong(o));
			long l = d.getLongInteger();
			assertEquals(o, l);
			
			d = new Data(DataTest.constructWeechatLong(o));
			l = d.getTime();
			assertEquals(o,l);
		}
	}

	@Test
	public void testGetString() throws UnsupportedEncodingException {
		String testCases[] = new String[] {
				"Hello World",
				"some long message",
				"another message nothing special about it at all",
				"Test UTF-8 support from issue #1. Norwegian characters æ, ø and å.",
				"¥ · £ · € · $ · ¢ · ₡ · ₢ · ₣ · ₤ · ₥ · ₦ · ₧ · ₨ · ₩ · ₪ · ₫ · ₭ · ₮ · ₯ · ₹",
				""
				};
		for(int i=0;i<testCases.length; i++) {
			String o = testCases[i];
			ByteBuffer bb = ByteBuffer.allocate(300);
			bb.order(ByteOrder.BIG_ENDIAN);
			byte[] t = o.getBytes("UTF-8");
			bb.putInt(t.length);
			bb.put(t);
				
			Data d = new Data(bb.array());
			String s = d.getString();
			assertEquals(o, s);
		}
	}
	@Test
	public void testGetStringNull() {
		ByteBuffer bb = ByteBuffer.allocate(6);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(-1);
		Data d = new Data(bb.array());
		String s = d.getString();
		assertNull(s);
	}
	
	@Test
	public void testGetBuffer() {
		byte test[] = new byte[1024];
		for(int i=0;i<test.length;i++) {
			test[i] = (byte) (i%255);
		}
		ByteBuffer bb = ByteBuffer.allocate(1028);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(test.length);
		bb.put(test);
			
		Data d = new Data(bb.array());
		byte[] b = d.getBuffer();
		for(int i=0;i<test.length;i++) {
			assertEquals(test[i], b[i]);
		}
	}
	@Test
	public void testGetBufferEmpty() {
		ByteBuffer bb = ByteBuffer.allocate(6);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(0);
		Data d = new Data(bb.array());
		byte[] b = d.getBuffer();
		assertEquals(0, b.length);
	}
	@Test
	public void testGetBufferNull() {
		ByteBuffer bb = ByteBuffer.allocate(6);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(-1);
		Data d = new Data(bb.array());
		byte[] b = d.getBuffer();
		assertNull(b);
	}
	
	
	@Test
	public void testGetPointer() throws UnsupportedEncodingException {
		long testcases[] = new long[] {
				0x1a2b3c4d5L,
				0x0,
				0x11111,
				0xdeadbeef,
		};
		for (long t: testcases) {
			String test = String.format("%x",t);
			ByteBuffer bb = ByteBuffer.allocate(270);
			bb.order(ByteOrder.BIG_ENDIAN);
			bb.put((byte)(test.length() & 0xFF));
			bb.put(test.getBytes("US-ASCII"));
			Data d = new Data(bb.array());
			
			String p = d.getPointer();
			assertEquals("0x" + test, p);
		}
	}
	
	@Test
	public void testGetHashtable() {
		//pass();
	}
	
	@Test
	public void testGetByteArray() {
		Data d = new Data(new byte[0]);
		assertEquals(0, d.getByteArray().length);
		
		d = new Data(new byte[]{5,1,2,3,4});
		d.getUnsignedInt();
		assertEquals(4,d.getByteArray()[0]);
	}
	
	@Test
	public void testGetEmpty() {
		Data d = new Data(new byte[0]);
		assertTrue(d.empty());
		
		d = new Data(new byte[]{1,2,3,4,5});
		d.getUnsignedInt();
		assertFalse(d.empty());
		d.getChar();
		assertTrue(d.empty());
	}
	
	
	// Tests that involve exceptions
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetIntegerException() {
		Data d = new Data(new byte[]{3}); // Missing data
		d.getUnsignedInt(); // Exception
	}
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetLongIntegerException1() {
		Data d = new Data(new byte[]{5}); // Missing data
		d.getLongInteger(); // Exception
	}
	@Test(expected = RuntimeException.class)
	public void testGetLongIntegerException2() {
		Data d = new Data(new byte[]{0}); // Get long int of length 0
		d.getLongInteger(); // Exception
	}
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetStringException() {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(bb.capacity());
		Data d = new Data(bb.array());
		d.getString(); // Exception, no character data
	}
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetBufferException() {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(bb.capacity());
		Data d = new Data(bb.array());
		d.getBuffer(); // Exception, no character data
	}
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetPointerException() {
		Data d = new Data(new byte[]{5}); // Missing data
		d.getPointer(); // Exceptin
	}

}
