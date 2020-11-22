package com.ubergeek42.WeechatAndroid.tabcomplete

import android.widget.EditText
import com.ubergeek42.WeechatAndroid.relay.Buffer


class LocalTabCompleter(
    private val buffer: Buffer,
    input: EditText,
) : TabCompleter(input) {
    override fun next() {
        if (replacements == null) {
            replacements = retrieveCompletions()
        }
        performCompletion()
    }

    private fun retrieveCompletions(): Iterator<Replacement> {
        val text = input.text ?: return EmptyReplacements

        // find the end of the word to be completed: "bla-bla nick|"
        val end = input.selectionStart
        if (end <= 0) return EmptyReplacements

        // find the beginning of the word to be completed "bla-bla |nick"
        var start = end
        while (start > 0 && text[start - 1] != ' ') start--

        // get the word to be completed, lowercase
        if (start == end) return EmptyReplacements
        val baseWord = text.subSequence(start, end).toString()

        // nicks is ordered in last used comes first way, so we just pick whatever comes first
        val matchingNicks = buffer.getMostRecentNicksMatching(baseWord)
        if (matchingNicks.size == 0) return EmptyReplacements

        return replacements(completions = matchingNicks,
                            start = start,
                            baseWord = baseWord,
                            suffix = if (start == 0) ": " else "")
    }
}