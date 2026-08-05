package com.ubergeek42.weechat.relay.protocol

import com.ubergeek42.weechat.BufferLine

class LineList(bufferLines: List<BufferLine>) : RelayObject(), List<BufferLine> by bufferLines {
}
