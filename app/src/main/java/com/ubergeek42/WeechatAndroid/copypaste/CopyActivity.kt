package com.ubergeek42.WeechatAndroid.copypaste

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.views.EditTextActivity
import com.ubergeek42.weechat.relay.connection.find


private const val EXTRA_BUFFER_POINTER = "buffer_pointer"
private const val EXTRA_SELECTED_LINE_POINTER = "selected_line_pointer"


fun launchCopyActivity(context: Context, bufferPointer: Long, selectedLinePointer: Long) {
    val intent = Intent(context, CopyActivity::class.java).apply {
        putExtra(EXTRA_BUFFER_POINTER, bufferPointer)
        putExtra(EXTRA_SELECTED_LINE_POINTER, selectedLinePointer)
    }
    context.startActivity(intent)
}

class CopyActivity : EditTextActivity(allowSoftKeyboard = false) {
    private var bufferPointer: Long = 0
    private var selectedLinePointer: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)
        window.decorView.setBackgroundColor(P.colorPrimary)

        bufferPointer = intent.getLongExtra(EXTRA_BUFFER_POINTER, 0)
        selectedLinePointer = intent.getLongExtra(EXTRA_SELECTED_LINE_POINTER, 0)

        ui.toolbar.apply {
            setTitle(R.string.dialog__copy__title)
            inflateMenu(R.menu.copy_dialog_fullscreen)
            setOnMenuItemClickListener listener@{ menuItem ->
                Select::id.find(menuItem.itemId)?.let { setBody(it) }
                true
            }
        }

        // On Oreo (API 26, 27) redrawing text can lead to the below crash due to a platform bug.
        // In this case, selecting a portion of text in the below call will trigger it.
        // Fixed in Pie, for Oreo the workaround is to use the software layer.
        //
        //     java.lang.ArrayIndexOutOfBoundsException: length=19; index=-8
        //     at android.text.DynamicLayout.getBlockIndex(DynamicLayout.java:646)
        //         at android.widget.Editor.drawHardwareAccelerated(Editor.java:1695)
        //         at android.widget.Editor.onDraw(Editor.java:1664)
        //
        // See: https://issuetracker.google.com/issues/67102093
        //      https://android-review.googlesource.com/c/platform/frameworks/base/+/634929
        if (Build.VERSION.SDK_INT < 28) {
            ui.text.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        setBody(Select.WithoutTimestamps)
    }

    private fun setBody(select: Select) {
        val buffer = BufferList.findByPointer(bufferPointer) ?: return
        val body = Body.build(buffer.getLinesCopy(), selectedLinePointer, select.textGetter)
        ui.text.setBody(body)
    }
}


private typealias TextGetter = (Line) -> String

@Suppress("unused")
private enum class Select(val id: Int, val textGetter: TextGetter) {
    WithTimestamps(R.id.menu_select_with_timestamps, Line::getTimestampedIrcLikeString),
    WithoutTimestamps(R.id.menu_select_without_timestamps, Line::getIrcLikeString),
    MessagesOnly(R.id.menu_select_messages_only, Line::getMessageString),
}


private data class Body(val text: CharSequence, val selectionStart: Int, val selectionEnd: Int) {
    companion object {
        fun build(lines: List<Line>, selectedLinePointer: Long, textGetter: TextGetter): Body {
            val text = StringBuilder()
            var selectionStart = -1
            var selectionEnd = -1

            lines.forEach { line ->
                if (line.pointer == selectedLinePointer) selectionStart = text.length
                text.append(textGetter(line))
                if (line.pointer == selectedLinePointer) selectionEnd = text.length
                text.append("\n")
            }

            return Body(text, selectionStart, selectionEnd)
        }
    }
}

private fun EditText.setBody(body: Body) {
    setText(body.text)
    if (body.selectionStart != -1 && body.selectionEnd != -1) post {
        requestFocus()
        setTextIsSelectable(true)
        selectTextCentering(body.selectionStart, body.selectionEnd)
    }
}


// this is a hacky way of centering the selection. EditText wants to show the whole selection,
// so selecting a bigger portion of text and then narrowing down the selection
// makes it appear closer to the center. this does not account for line height;
// also the number 400 is rather random and might not work as well on other devices
// todo find a better way of centering selection
private fun EditText.selectTextCentering(selectionStart: Int, selectionEnd: Int) {
    selectTextSafe(selectionStart - 400, selectionEnd + 400)
    post { selectTextSafe(selectionStart, selectionEnd) }
}


private fun EditText.selectTextSafe(selectionStart: Int, selectionEnd: Int) {
    setSelection(selectionStart.coerceIn(text.indices),
                 selectionEnd.coerceIn(text.indices))
}


