package com.ubergeek42.weechat;

import static org.junit.Assert.*;

import org.junit.Test;

public class ColorTest {

	@Test
	public void testStripColors() {
		String teststr = "\u001903asd\u001C";
		String oracle = "asd";
		assertEquals(Color.stripColors(teststr), oracle);
	}
	@Test
	public void testStripColors2() {
		// Happens on freenode with title set to something containing colors, the 0F seems to come from what should be 0x1C, or reset colors/attributes
		String teststr = "red green cyan.\u000F some normal text";
		String oracle = "red green cyan. some normal text";
		assertEquals(Color.stripColors(teststr), oracle);
	}
    @Test
    public void testStripColorInIset() {
        String isetTitle = "Interactive set (iset.pl v1.0)  |  Filter: \u0019F08*relay*\u0019F00  |  16 options";
        String expected = "Interactive set (iset.pl v1.0)  |  Filter: *relay*  |  16 options";
        assertEquals(isetTitle, Color.stripIRCColors(isetTitle));
        assertNotSame(expected, Color.stripIRCColors(isetTitle));
        assertEquals(expected, Color.stripColors(isetTitle));

    }
}
/*
00 00 00 21
72 65 64 20 67 72 65 65 6E 20 63 79 red green cy
61 6E 0F 20 73 6F 6D 65 20 6E 6F 72 6D 61 6C 20 74 65 78 74 an. some normal text
*/

/* Test string that displays a box after the word cyan(is the clear color attribute)
00 00 00 A1 00 00 00 00 15 5F 62 75 66 66 65 72 5F 74 69 74 ........._buffer_tit
6C 65 5F 63 68 61 6E 67 65 64 68 64 61 00 00 00 06 62 75 66 le_changedhda....buf
66 65 72 00 00 00 22 6E 75 6D 62 65 72 3A 69 6E 74 2C 66 75 fer..."number:int,fu
6C 6C 5F 6E 61 6D 65 3A 73 74 72 2C 74 69 74 6C 65 3A 73 74 ll_name:str,title:st
72 00 00 00 01 06 63 36 36 36 64 38 00 00 00 06 00 00 00 18 r.....c666d8........
69 72 63 2E 66 72 65 65 6E 6F 64 65 2E 23 75 62 65 72 67 65 irc.freenode.#uberge
65 6B 34 32 00 00 00 21 72 65 64 20 67 72 65 65 6E 20 63 79 ek42...!red green cy
61 6E 0F 20 73 6F 6D 65 20 6E 6F 72 6D 61 6C 20 74 65 78 74 an. some normal text
7A                                                          z
*/