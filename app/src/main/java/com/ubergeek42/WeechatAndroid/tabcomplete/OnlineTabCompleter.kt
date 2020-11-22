package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.protocol.Hdata
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class OnlineTabCompleter(
    private val lifecycle: Lifecycle,
    private val buffer: Buffer,
    private val input: EditText,
) : TabCompleter {
    companion object { @Root private val kitty = Kitty.make() }

    private var job: Job? = null
    private lateinit var replacements: Iterator<Replacement>

    @Suppress("CascadeIf")
    override fun next() {
        if (this::replacements.isInitialized) {
            performCompletion()
        } else if (job != null) {
            return  // job is initialized, but it hasn't run to completion yet — do nothing & wait
        } else {
            job = lifecycle.coroutineScope.launch {
                suppress<Exception>(showToast = true) {
                    replacements = fetchCompletions()
                    performCompletion()
                }
            }
        }
    }

    // the completions can be (pos_start..pos_end, base_word, completion):
    //   "s|": 0..0, "s", "squirrel"
    //   "/|s": 1..1, "", "# "
    // in the first example, the range 0..0 means that we have to replace one character,
    // as "end" is inclusive (docs: "index of last char to replace")
    // in the second example, however, the situation is the same while base word is empty.
    // therefore we're using base word instead of end to determine what exactly should be replaced
    private suspend fun fetchCompletions(): Iterator<Replacement> {
        val message = "completion 0x%x %d %s".format(buffer.pointer, input.selectionStart, input.text)
        val relayObject = sendMessageAndGetResponse(message) as Hdata

        if (relayObject.count == 0) return EmptyReplacements

        val item = relayObject.getItem(0)
        val completions = item.getItem("list").asArray().asStringArray()

        if (completions.isEmpty()) return EmptyReplacements

        val start = item.getItem("pos_start").asInt()
        val addSpace = item.getItem("add_space").asInt() == 1
        var completion = item.getItem("base_word").asString()

        var index = 0

        return sequence {
            while (true) {
                val previousCompletionLength = completion.length
                completion = completions[index++ % completions.size]
                if (addSpace) completion += " "
                yield(Replacement(start, start + previousCompletionLength, completion))
            }
        }.iterator()
    }

    private var replacingText = false

    private fun performCompletion() {
        if (replacements === EmptyReplacements) return
        val (start, end, text) = replacements.next()
        replacingText = true
        input.text.replace(start, end, text)
        replacingText = false
    }

    override fun cancel(): Boolean {
        job?.cancel()
        return !replacingText
    }
}


private data class Replacement(
    val start: Int,
    val end: Int,
    val text: String,
)

private val EmptyReplacements = emptyList<Replacement>().iterator()