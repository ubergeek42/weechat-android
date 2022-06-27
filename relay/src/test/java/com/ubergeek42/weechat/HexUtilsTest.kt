package com.ubergeek42.weechat

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


const val string001077 = "001077abCdfF"
val byteArray001077 = byteArrayOf(0x00, 0x10, 0x77, 0xab.toByte(), 0xcd.toByte(), 0xff.toByte())


internal class HexUtilsTest {
    @Test fun fromHexStringToByteArray() {
        assertArrayEquals("".fromHexStringToByteArray(), byteArrayOf())
        assertArrayEquals(string001077.fromHexStringToByteArray(), byteArray001077)

        assertThrows<IllegalArgumentException> {
            "hi".fromHexStringToByteArray()
        }

        assertThrows<IllegalArgumentException> {
            "123".fromHexStringToByteArray()
        }
    }

    @Test fun toHexStringLowercase() {
        assertEquals(byteArrayOf().toHexStringLowercase(), "")
        assertEquals(byteArray001077.toHexStringLowercase(), string001077.lowercase())
    }
}