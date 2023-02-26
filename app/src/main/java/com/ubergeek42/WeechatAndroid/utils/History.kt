package com.ubergeek42.WeechatAndroid.utils

import android.os.Parcel
import android.widget.EditText
import androidx.core.text.getSpans
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.upload.ShareSpan

class History : java.io.Serializable {
    companion object {
        // Sentinel index representing the most recent end of the message history.
        private const val END = -1

        private const val MAX_MESSAGE_LENGTH = 2000
        private const val MAX_HISTORY_LENGTH = 40
    }

    class Message : java.io.Serializable {
        lateinit var content: String
        var selectionStart: Int = 0
        var selectionEnd: Int = 0

        constructor(editText: EditText) {
            updateFromEdit(editText)
        }

        constructor(source: Parcel) {
            content = source.readString() ?: ""
            selectionStart = source.readInt()
            selectionStart = source.readInt()
        }


        fun updateFromEdit(editText: EditText) {
            content = Utils.cut(editText.text.toString(), MAX_MESSAGE_LENGTH)
            // Input length might be shorter because of the text ellipsis from above.
            selectionStart = minOf(content.length, editText.selectionStart)
            selectionEnd = minOf(content.length, editText.selectionEnd)
        }

        fun applyToEdit(editText: EditText) {
            editText.setText(content)
            editText.setSelection(selectionStart, selectionEnd)
        }
    }

    private var messages: MutableList<Message> = mutableListOf()
    private var index = END

    enum class Direction { Older, Newer }

    // Save message iff it is not empty and not equal to the most recent message.
    // Will remove the oldest message if the message size goes above the maximum history size.
    private fun maybeSaveMessage(editText: EditText): Boolean {
        val message = Message(editText)
        val content = message.content
        if (content.trim().isNotEmpty()) {
            val last = messages.getOrNull(0)?.content
            if (last?.equalsIgnoringUselessSpans(content) != true) {
                messages.add(0, message)
                if (messages.size > MAX_HISTORY_LENGTH) messages.removeLast()
                return true
            }
        }
        return false
    }

    fun navigate(editText: EditText, direction: Direction) {
        if (editText.text.getSpans<ShareSpan>().isNotEmpty()) {
            // The editText contains non-uploaded ShareSpans that would be lost by navigating away.
            // Bail out early to prevent data loss.
            Toaster.ErrorToast.show(R.string.error__etc__cannot_navigate_input_history_if_sharespans_in_input)
            return
        }
        var newIndex = when (direction) {
            Direction.Older -> index + 1
            Direction.Newer -> index - 1
        }
        if (index == END) {
            // Not in history yet. Push current edit to history.
            val wasInserted = maybeSaveMessage(editText)
            // Special case when saving from end and going older: since we've just inserted, we need
            // to skip over the message that was just saved.
            if (wasInserted && direction == Direction.Older) newIndex += 1
        } else {
            // In history already. Save changes before navigating away.
            messages[index].updateFromEdit(editText)
        }
        if (newIndex < END) newIndex = END
        if (newIndex >= messages.size) newIndex = messages.size - 1
        if (newIndex == END) {
            if (editText.text.isNotEmpty()) editText.setText("")
        } else if (newIndex != index) {
            messages[newIndex].applyToEdit(editText)
        }
        index = newIndex
    }

    fun reset(editText: EditText) {
        maybeSaveMessage(editText)
        index = END
    }
}