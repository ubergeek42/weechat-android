package com.ubergeek42.weechat

import com.ubergeek42.weechat.relay.protocol.BufferBody
import com.ubergeek42.weechat.relay.protocol.HdataEntry
import com.ubergeek42.weechat.relay.protocol.LineBody
import java.time.Instant
import java.util.Date

class BufferLine {
    val bufferPointer: Long
    val id: Long?
    val pointer: Long?
    val timestamp: Long
    val prefix: String?
    val message: String?
    val displayed: Boolean
    val highlight: Boolean
    val notifyLevel: Byte?
    val tags: Array<String>?

    constructor(entry: HdataEntry) {
        bufferPointer = entry.getPointerLong("buffer")!!
        pointer = entry.pointerLong
        id = entry.getInt("id")?.toLong()
        timestamp = entry.getItem("date")?.asTime()?.time!!
        prefix = entry.getStringOrNull("prefix")
        message = entry.getStringOrNull("message")
        displayed = entry.getChar("displayed") == 1.toChar()
        highlight = entry.getChar("highlight") == 1.toChar()
        notifyLevel = entry.getByteOrNull("notify_level")
        tags = entry.getStringArrayOrNull("tags_array")
    }

    constructor(entry: LineBody, bufferId: Long) {
        bufferPointer = bufferId
        pointer = null
        id = entry.id
        timestamp = Instant.parse(entry.date).toEpochMilli()
        prefix = entry.prefix
        message = entry.message
        displayed = entry.displayed
        highlight = entry.highlight
        notifyLevel = entry.notifyLevel.toByte() // FIXME???
        tags = entry.tags.toTypedArray()
    }
}
