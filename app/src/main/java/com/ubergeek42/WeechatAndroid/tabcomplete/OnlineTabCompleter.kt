package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.protocol.Hdata
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.properties.Delegates

class OnlineTabCompleter() : TabCompleter() {
    @Root
    private val kitty = Kitty.make()

    private lateinit var job: Job;

    private lateinit var tcBaseString: String // the entire original input
    private var tcIndex by Delegates.notNull<Int>() // index used when looping over tcCompletionResults
    private lateinit var tcCompletionResults: Array<String>
    private var tcAddSpace by Delegates.notNull<Boolean>() // to add a space after completion
    private var tcPosStart by Delegates.notNull<Int>() // beginning of section to be replaced by one of the suggestions
    private var tcPosEnd by Delegates.notNull<Int>() // end of said section in tcBaseString

    private lateinit var tcBaseWord: String
    private var tcSelectionIndex by Delegates.notNull<Int>() // where the cursor was when tab was pressed

    constructor(lifecycle: Lifecycle, buffer: Buffer, uiInput: EditText) : this() {
        this.lifecycle = lifecycle
        this.buffer = buffer
        this.uiInput = uiInput
        this.tcBaseString = uiInput.text.toString()
        this.tcSelectionIndex = uiInput.selectionStart
    }

    override fun next() {
        val txt = uiInput.text ?: return

        if (this::tcCompletionResults.isInitialized) {
            // tcCompletionResults.size == 0 if exception thrown; null out upon next user input
            if (tcCompletionResults.size > 1) {
                val resultsSize = tcCompletionResults.size
                tcIndex = (tcIndex + 1) % resultsSize
                shouldntNullOut = true
                val completionText = tcCompletionResults[tcIndex] + if (tcAddSpace) " " else ""
                txt.replace(tcPosStart, tcPosEnd, completionText)
                tcPosEnd = tcPosStart + completionText.length
            }
            return
        }

        cancel() // could be initialized but response hasn't arrived yet (e.g. tab pressed rapidly)
        job = this.lifecycle.coroutineScope.launch {
            runCatching {
                val completionMessage = "completion 0x%x %d %s"
                        .format(buffer.pointer, uiInput.selectionStart, uiInput.text.toString())
                val relayObject = sendMessageAndGetResponse(completionMessage) as Hdata
                kitty.info("got relay completion response: $relayObject")

                if (relayObject.count == 0)
                    throw NoSuchElementException("No completion results from relay.")

                val item = relayObject.getItem(0)
                tcCompletionResults =
                        item.getItem("list").asArray().asStringArray()

                tcAddSpace = item.getItem("add_space").asInt() == 1
                tcPosStart = item.getItem("pos_start").asInt()
                tcPosEnd = item.getItem("pos_end").asInt()
                tcBaseWord = item.getItem("base_word").asString()
                tcIndex = 0

                if (tcCompletionResults.isEmpty())
                    throw NoSuchElementException("No completion results from relay.")

                if (!isActive) return@launch

                // let BufferFragment null this out if only one completion result
                if (tcCompletionResults.size > 1)
                    shouldntNullOut = true

                Weechat.runOnMainThread {
                    val completionText = tcCompletionResults[0] + if (tcAddSpace) " " else ""
                    txt.replace(tcPosStart, min(++tcPosEnd, txt.length), completionText)
                    tcPosEnd = tcPosStart + completionText.length
                }
            }.onFailure { kitty.info("Error while tab completing", it); }
        }
    }

    override fun cancel() {
        if (this::job.isInitialized) {
            job.cancel()
        }
    }
}