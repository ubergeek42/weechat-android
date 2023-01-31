package com.ubergeek42.WeechatAndroid.utils

import android.os.Parcel
import android.widget.EditText
import androidx.core.text.getSpans
import com.ubergeek42.WeechatAndroid.upload.ShareSpan

class History : java.io.Serializable {
    companion object {
        // Sentinel index representing the most recent end of the message history.
        private const val END = -1

        private const val MAX_MESSAGE_LENGTH = 2000
        private const val MAX_HISTORY_LENGTH = 40
    }

    class SpanSpec(var span: ShareSpan, var start: Int, var end: Int, var flags: Int) :
        java.io.Serializable {
        private fun readObject(inp: java.io.ObjectInputStream) {
            val p = Parcel.obtain()
            val bytes = inp.readObject() as Array<Byte>
            p.unmarshall(bytes.toByteArray(), 0, bytes.size)
            // This fails :(
            this.span = p.readParcelable(ShareSpan::class.java.classLoader)!!
            p.recycle()
            this.start = inp.readInt()
            this.end = inp.readInt()
            this.flags = inp.readInt()
        }

        private fun writeObject(out: java.io.ObjectOutputStream) {
            val p = Parcel.obtain()
            p.writeParcelable(span, 0)
            val bytes = p.marshall().toTypedArray()
            p.recycle()
            out.writeObject(bytes)
            out.writeInt(start)
            out.writeInt(end)
            out.writeInt(flags)
        }
    }

    class Message : java.io.Serializable {
        lateinit var content: String
        var selectionStart: Int = 0
        var selectionEnd: Int = 0
        var shareSpans: List<SpanSpec> = listOf()

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
            shareSpans = editText.text.getSpans<ShareSpan>().asIterable().map {
                SpanSpec(
                    it,
                    editText.text.getSpanStart(it),
                    editText.text.getSpanEnd(it),
                    editText.text.getSpanFlags(it)
                )
            }.toList()
        }

        fun applyToEdit(editText: EditText) {
            editText.setText(content)
            editText.setSelection(selectionStart, selectionEnd)
            for (span in shareSpans) {
                editText.text.setSpan(span.span, span.start, span.end, span.flags)
            }
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