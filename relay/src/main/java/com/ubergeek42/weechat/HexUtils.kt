package com.ubergeek42.weechat


private fun Char.fromHexCharToInt(): Int {
    return when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> throw IllegalArgumentException("Not a hex digit: $this")
    }
}


fun String.fromHexStringToByteArray(): ByteArray {
    if (length % 2 != 0) {
        throw IllegalArgumentException("Input must have even character count")
    }

    val outputBytes = ByteArray(length / 2)

    outputBytes.indices.forEach { outputIndex ->
        val digit1 = this[outputIndex * 2].fromHexCharToInt()
        val digit2 = this[outputIndex * 2 + 1].fromHexCharToInt()
        outputBytes[outputIndex] = (digit1 * 16 + digit2).toByte()
    }

    return outputBytes
}


fun ByteArray.toHexStringLowercase(): String {
    val outputCharacters = CharArray(size * 2)

    this.forEachIndexed { inputIndex, inputByte ->
        val inputByteIntValue = inputByte.toInt() and 0xff
        outputCharacters[inputIndex * 2] = HEX_DIGITS[inputByteIntValue ushr 4]
        outputCharacters[inputIndex * 2 + 1] = HEX_DIGITS[inputByteIntValue and 0x0f]
    }

    return String(outputCharacters)
}


private val HEX_DIGITS = "0123456789abcdef".toCharArray()
