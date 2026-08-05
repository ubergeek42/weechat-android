@file:Suppress("NOTHING_TO_INLINE")

package com.ubergeek42.WeechatAndroid.relay

import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.HdataEntry

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

inline fun List<com.ubergeek42.weechat.Buffer>.forEachBufferSpec(block: (spec: BufferSpec) -> Unit) {
    forEach { entry ->
        val spec = BufferSpec(entry)
        block(spec)
    }
}

inline fun List<com.ubergeek42.weechat.Buffer>.forEachExistingBuffer(block: (spec: BufferSpec, buffer: Buffer) -> Unit) {
    forEachBufferSpec { spec ->
        BufferList.findByPointer(spec.pointer)?.let { buffer -> block(spec, buffer) }
    }
}


val Long.as0x get() = "0x" + java.lang.Long.toUnsignedString(this, 16)

val String.from0x: Long get() = java.lang.Long.parseUnsignedLong(this.substring(2), 16)

val String.from0xOrNull get() = try { from0x } catch (e: NumberFormatException) { null }