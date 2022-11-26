package com.ubergeek42.WeechatAndroid.utils

import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import androidx.core.os.bundleOf
import com.ubergeek42.WeechatAndroid.service.P

class History {
    private var index = -1
    private var userInput: Editable? = null
    private var selectionStart: Int? = null
    private var selectionEnd: Int? = null

    fun navigateOffset(editText: EditText, offset: Int) {
        if (index == -1) {
            userInput = editText.text
            selectionStart = editText.selectionStart
            selectionEnd = editText.selectionEnd
        }
        val newIndex = index + offset
        messageAt(newIndex)?.let {
            editText.text = it
            editText.post {
                if (newIndex == -1) {
                    // Restore user selection.
                    editText.setSelection(selectionStart ?: 0, selectionEnd ?: editText.length())
                } else {
                    // Go to end for sent messages.
                    editText.setSelection(editText.length())
                }
            }
            index = newIndex
        }
    }

    fun save(): Bundle = bundleOf(
        Pair("index", index),
        Pair("userInput", userInput),
        Pair("selStart", selectionStart),
        Pair("selEnd", selectionEnd),
    )

    fun restore(bundle: Bundle) {
        index = bundle.getInt("index", -1)
        userInput = bundle.get("userInput") as Editable?
        selectionStart = bundle.getInt("selStart", -1).let { if (it == -1) null else it }
        selectionEnd = bundle.getInt("selEnd", -1).let { if (it == -1) null else it }
    }

    private fun messageAt(index: Int): Editable? =
        if (index == -1) userInput else P.sentMessages.getOrNull(P.sentMessages.size - 1 - index)
            ?.let { Editable.Factory.getInstance().newEditable(it) }
}