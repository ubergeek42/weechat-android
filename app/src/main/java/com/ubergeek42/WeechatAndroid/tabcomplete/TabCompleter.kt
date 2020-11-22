package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.weechat.relay.RelayMessageHandler
import com.ubergeek42.weechat.relay.connection.RelayConnection
import com.ubergeek42.weechat.relay.protocol.RelayObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface TabCompleter {
    fun next()

    // returns true if cancel was successful
    fun cancel(): Boolean

    companion object {
        @JvmStatic
        fun obtain(lifecycle: Lifecycle, buffer: Buffer, uiInput: EditText): TabCompleter {
            return if (RelayConnection.weechatVersion >= 0x2090000) {
                OnlineTabCompleter(lifecycle, buffer, uiInput)
            } else {
                LocalTabCompleter(buffer, uiInput)
            }
        }
    }
}

suspend fun sendMessageAndGetResponse(message: String) = suspendCancellableCoroutine<RelayObject> {
    val handler = object : RelayMessageHandler {
        override fun handleMessage(obj: RelayObject, id: String) {
            it.resume(obj)
            BufferList.removeMessageHandler(id, this);
        }
    }

    val id = BufferList.addOneOffMessageHandler(handler)
    Events.SendMessageEvent.fire("($id) $message")
}