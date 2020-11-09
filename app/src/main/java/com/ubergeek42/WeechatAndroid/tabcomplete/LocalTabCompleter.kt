package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import kotlin.properties.Delegates


class LocalTabCompleter() : TabCompleter() {
    @Root
    private val kitty = Kitty.make()

    private lateinit var tcMatches: ArrayList<String>
    private var tcIndex by Delegates.notNull<Int>()
    private var tcWordStart by Delegates.notNull<Int>()
    private var tcWordEnd by Delegates.notNull<Int>()

    constructor(lifecycle: Lifecycle, buffer: Buffer, uiInput: EditText) : this() {
        this.lifecycle = lifecycle
        this.buffer = buffer
        this.uiInput = uiInput
    }

    override fun next() {
        val txt = uiInput.text ?: return

        if (!this::tcMatches.isInitialized) {
            // find the end of the word to be completed
            // blabla nick|
            tcWordEnd = uiInput.selectionStart
            if (tcWordEnd <= 0) return

            // find the beginning of the word to be completed
            // blabla |nick
            tcWordStart = tcWordEnd
            while (tcWordStart > 0 && txt[tcWordStart - 1] != ' ') tcWordStart--

            // get the word to be completed, lowercase
            if (tcWordStart == tcWordEnd) return
            val prefix = txt.subSequence(tcWordStart, tcWordEnd).toString().toLowerCase()

            // compute a list of possible matches
            // nicks is ordered in last used comes first way, so we just pick whatever comes first
            // if computed list is empty, abort
            tcMatches = buffer.getMostRecentNicksMatching(prefix)
            if (tcMatches.size == 0) return
            tcIndex = 0
        } else {
            if (tcMatches.size == 0) return
            tcIndex = (tcIndex + 1) % tcMatches.size
        }

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word
        var nick = tcMatches[tcIndex]
        if (tcWordStart == 0) nick += ": "
        shouldntNullOut = true
        txt.replace(tcWordStart, tcWordEnd, nick)
        tcWordEnd = tcWordStart + nick.length
        uiInput.setSelection(tcWordEnd)
    }

    override fun cancel() {
        // no-op
    }

}