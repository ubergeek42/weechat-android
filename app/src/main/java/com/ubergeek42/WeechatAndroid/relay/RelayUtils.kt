@file:Suppress("NOTHING_TO_INLINE")

package com.ubergeek42.WeechatAndroid.relay

import com.ubergeek42.weechat.relay.protocol.Hashtable
import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.HdataEntry
import com.ubergeek42.weechat.relay.protocol.RelayObject
import java.lang.NumberFormatException
import com.ubergeek42.weechat.relay.protocol.Array as WeeArray

inline fun HdataEntry.getInt(id: String): Int = getItem(id).asInt()
inline fun HdataEntry.getChar(id: String): Char = getItem(id).asChar()
inline fun HdataEntry.getString(id: String): String = getItem(id).asString()
inline fun HdataEntry.getHashtable(id: String): Hashtable = getItem(id) as Hashtable

inline fun HdataEntry.getPointerLong(id:String): Long = getItem(id).asPointerLong()
inline fun HdataEntry.getArray(id:String): WeeArray = getItem(id).asArray()

inline fun HdataEntry.getStringOrNull(id: String): String? = getItem(id)?.asString()
inline fun HdataEntry.getIntOrNull(id: String): Int? = getItem(id)?.asInt()
inline fun HdataEntry.getByteOrNull(id: String): Byte? = getItem(id)?.asByte()

inline fun HdataEntry.getStringArrayOrNull(id: String): Array<String>? = getItem(id)?.let {
    return@let if (it.type == RelayObject.WType.ARR) {
        it.asArray().asStringArray()
    } else {
        null
    }
}


inline fun Hdata.forEach(block: (HdataEntry) -> Unit) {
    for (i in 0 until count) {
        block(getItem(i))
    }
}

inline fun Hdata.forEachReversed(block: (HdataEntry) -> Unit) {
    for (i in count - 1 downTo 0) {
        block(getItem(i))
    }
}

inline fun Hdata.forEachBufferSpec(block: (spec: BufferSpec) -> Unit) {
    forEach { entry ->
        val spec = BufferSpec(entry)
        block(spec)
    }
}

inline fun Hdata.forEachExistingBuffer(block: (spec: BufferSpec, buffer: Buffer) -> Unit) {
    forEachBufferSpec { spec ->
        BufferList.findByPointer(spec.pointer)?.let { buffer -> block(spec, buffer) }
    }
}


val Long.as0x get() = "0x" + java.lang.Long.toUnsignedString(this, 16)

val String.from0x: Long get() = java.lang.Long.parseUnsignedLong(this.substring(2), 16)

val String.from0xOrNull get() = try { from0x } catch (e: NumberFormatException) { null }