package com.ubergeek42.weechat.relay.protocol;

import static org.junit.Assert.*;

import org.junit.Test;

public class DataTest {

	@Test
	public void testGetChar() {
		for(int o=0;o<256;o++) {
			Data d = new Data(new byte[]{(byte) (o & 0xFF)});
			char c = d.getChar();
			assertEquals(c, o);
		}
	}

	@Test
	public void testGetByte() {
		for(int o=0;o<256;o++) {
			Data d = new Data(new byte[]{(byte) (o & 0xFF)});
			int b = d.getByte();
			assertEquals(b, o);
		}
	}

}
