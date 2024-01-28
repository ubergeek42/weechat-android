package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.RelayObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class OnlineTabCompleter(
    private val lifecycle: Lifecycle,
    private val buffer: Buffer,
    input: EditText,
) : TabCompleter(input) {
    private var job: Job? = null

    @Suppress("CascadeIf")
    override fun next() {
        if (replacements != null) {
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
    //   "ня s|": 5..4, "s", "squirrel"
    // in the first example, the range 0..0 means that we have to replace one character,
    // as "end" is inclusive (docs: "index of last char to replace")
    // in the second example, however, the situation is the same while base word is empty.
    // also, if unicode characters are present, weird things happen...
    // so instead we rely on base word, assuming that it ends on selection end
    private suspend fun fetchCompletions(): Iterator<Replacement> {
        val selectionStart = input.selectionStart
        val message = "completion ${buffer.pointer.as0x} $selectionStart ${input.text}"

        val (completions, baseWord, addSpace) = queryWeechat(message).asCompletions()
                ?: return EmptyReplacements

        return replacements(completions = completions,
                            start = selectionStart - baseWord.length,
                            baseWord = baseWord,
                            suffix = if (addSpace) " " else "")
    }

    override fun cancel(): Boolean {
        job?.cancel()
        return super.cancel()
    }
}


private data class Completions(
    val completions: List<String>,
    val baseWord: String,
    val addSpace: Boolean
)

private fun RelayObject.asCompletions(): Completions? {
    if (this !is Hdata || count == 0) return null

    val item = getItem(0)
    val completions = item.getItem("list").asArray().asStringArray().toList()

    if (completions.isEmpty()) return null

    val baseWord = item.getItem("base_word").asString()
    val addSpace = item.getItem("add_space").asInt() == 1

    return Completions(completions, baseWord, addSpace)
}