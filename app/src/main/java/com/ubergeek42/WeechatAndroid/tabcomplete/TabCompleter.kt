package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.RelayMessageHandler
import com.ubergeek42.weechat.relay.connection.Handshake
import com.ubergeek42.weechat.relay.protocol.RelayObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

abstract class TabCompleter(val input: EditText) {
    // perform next completion
    abstract fun next()

    // returns true if cancellation was successful
    // completions change text, but we also want to forget about completions when the user types.
    // so when there's text change, we need to determine if it originates with us
    open fun cancel() = !replacingText

    protected var replacements: Iterator<Replacement>? = null
    private var replacingText = false

    protected fun performCompletion() {
        if (replacements === EmptyReplacements) return
        val (start, end, text) = replacements?.next() ?: return

        replacingText = true
        suppress<Exception> { input.text.replace(start, end, text) }
        replacingText = false
    }

    companion object {
        @Root private val kitty = Kitty.make()

        @JvmStatic
        fun obtain(lifecycle: Lifecycle, buffer: Buffer, input: EditText): TabCompleter {
            val localCompleter = LocalTabCompleter(buffer, input)

            return if (canDoOnlineCompletions() && localCompleter.lacksCompletions()) {
                OnlineTabCompleter(lifecycle, buffer, input)
            } else {
                localCompleter
            }
        }

        @JvmStatic
        private fun canDoOnlineCompletions() = Handshake.weechatVersion >= 0x2090000
    }
}

suspend fun queryWeechat(message: String) = suspendCancellableCoroutine<RelayObject> {
    val handler = BufferList.HdataHandler { obj, _ -> it.resume(obj) }
    val id = BufferList.addOneOffMessageHandler(handler)
    Events.SendMessageEvent.fire("($id) $message")
}


data class Replacement(
    val start: Int,
    val end: Int,
    val text: String,
)

val EmptyReplacements = emptyList<Replacement>().iterator()

fun replacements(completions: List<String>, start: Int, baseWord: String, suffix: String): Iterator<Replacement> {
    var completion = baseWord
    var index = 0

    return sequence {
        while (true) {
            val previousCompletionLength = completion.length
            completion = completions[index++ % completions.size] + suffix
            yield(Replacement(start, start + previousCompletionLength, completion))
        }
    }.iterator()
}
