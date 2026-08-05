package com.ubergeek42.weechat

import com.ubergeek42.weechat.relay.protocol.BufferBody
import com.ubergeek42.weechat.relay.protocol.HdataEntry

class Buffer {
    val pointer: Long
    val number: Int
    val fullName: String
    val shortName: String?
    val title: String?
    val notify: Int?
    // FIXME: val type: Hashtable
    val hidden: Boolean

    constructor(entry: HdataEntry) {
        pointer = entry.pointerLong
        number = entry.getInt("number")!!
        fullName = entry.getString("full_name")!!
        shortName = entry.getStringOrNull("short_name")
        title = entry.getStringOrNull("title")
        notify = entry.getIntOrNull("notify")
        // FIXME: type = entry.getHashtable("local_variables")
        hidden = entry.getIntOrNull("hidden") == 1
    }

    constructor(entry: BufferBody) {
        pointer = entry.id
        number = entry.number
        fullName = entry.name
        shortName = entry.shortName
        title = entry.title
        notify = 0 // FIXME: ???
        // FIXME: type = entry.local_variables.type
        hidden = entry.hidden
    }
}
