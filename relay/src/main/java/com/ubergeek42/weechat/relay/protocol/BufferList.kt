package com.ubergeek42.weechat.relay.protocol

import com.ubergeek42.weechat.Buffer

class BufferList(buffers: List<Buffer>) : RelayObject(), List<Buffer> by buffers {
}
