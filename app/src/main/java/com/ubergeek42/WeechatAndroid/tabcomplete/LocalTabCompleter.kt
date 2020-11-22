package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import kotlin.properties.Delegates


class LocalTabCompleter(
    private val buffer: Buffer,
    private val uiInput: EditText,
) : TabCompleter {
    companion object { @Root private val kitty = Kitty.make() }

    private lateinit var matches: ArrayList<String>
    private var index = 0
    private var start = 0
    private var end = 0


    override fun next() {
        val txt = uiInput.text ?: return

        if (!this::matches.isInitialized) {
            // find the end of the word to be completed
            // blabla nick|
            end = uiInput.selectionStart
            if (end <= 0) return

            // find the beginning of the word to be completed
            // blabla |nick
            start = end
            while (start > 0 && txt[start - 1] != ' ') start--

            // get the word to be completed, lowercase
            if (start == end) return
            val prefix = txt.subSequence(start, end).toString().toLowerCase()

            // compute a list of possible matches
            // nicks is ordered in last used comes first way, so we just pick whatever comes first
            // if computed list is empty, abort
            matches = buffer.getMostRecentNicksMatching(prefix)
            if (matches.size == 0) return
            index = 0
        } else {
            if (matches.size == 0) return
            index = (index + 1) % matches.size
        }

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word
        var nick = matches[index]
        if (start == 0) nick += ": "
        //shouldntNullOut = true
        txt.replace(start, end, nick)
        end = start + nick.length
        uiInput.setSelection(end)
    }

    override fun cancel() = true

}