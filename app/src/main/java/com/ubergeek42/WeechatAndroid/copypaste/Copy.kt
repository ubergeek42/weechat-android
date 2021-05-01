package com.ubergeek42.WeechatAndroid.copypaste

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.ClipboardManager
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.dialogs.FancyAlertDialogBuilder
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.ThemeFix
import com.ubergeek42.WeechatAndroid.views.LineView
import com.ubergeek42.weechat.relay.connection.find
import java.lang.StringBuilder
import java.util.*


fun showCopyDialog(lineView: LineView, bufferPointer: Long) {
    Copy(lineView.context, bufferPointer, lineView, lineView.tag as Line)
            .buildCopyDialog()
            .show()
}


@Suppress("unused")
private enum class Select(val id: Int, val textGetter: (Line) -> String) {
    WithTimestamps(R.id.menu_select_with_timestamps, Line::timestampedIrcLikeString),
    WithoutTimestamps(R.id.menu_select_without_timestamps, Line::ircLikeString),
    MessagesOnly(R.id.menu_select_messages_only, Line::messageString),
}


private class Copy(
    private val context: Context,
    private val bufferPointer: Long,
    private val sourceLineView: LineView,
    private val sourceLine: Line,
) {
    private fun getSourceLines(): List<String> {
        return mutableListOf<String>().apply {
            if (sourceLine.prefixString.isNotEmpty()) add(sourceLine.ircLikeString)
            add(sourceLine.messageString)
            addAll(sourceLineView.urls.map(URLSpan::getURL))
        }.distinct()
    }

    fun buildCopyDialog(): Dialog {
        val dialog = FancyAlertDialogBuilder(context).create()
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_copy, null) as ViewGroup

        layout.findViewById<TextView>(R.id.title).setText(R.string.dialog__copy__title)

        layout.findViewById<RecyclerView>(R.id.list).adapter =
                CopyAdapter(context, getSourceLines()) { item ->
            setClipboard(item)
            dialog.dismiss()
        }

        layout.findViewById<ImageButton>(R.id.select_text).setOnClickListener {
            buildFullScreenCopyDialog()?.show()
            dialog.dismiss()
        }

        dialog.setView(layout)
        return dialog
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buildBody(select: Select): Body? {
        val lines = (BufferList.findByPointer(bufferPointer) ?: return null).getLinesCopy()
        val lineTextGetter = select.textGetter

        val text = StringBuilder()
        var selectionStart = -1
        var selectionEnd = -1

        lines.forEach { line ->
            if (line.pointer == sourceLine.pointer) selectionStart = text.length
            text.append(lineTextGetter(line))
            if (line.pointer == sourceLine.pointer) selectionEnd = text.length
            text.append("\n")
        }

        return Body(text, selectionStart, selectionEnd)
    }

    private fun buildFullScreenCopyDialog(): Dialog? {
        val dialog = AlertDialog.Builder(context, R.style.FullScreenAlertDialogTheme).create()
        val layout = LayoutInflater.from(context)
                .inflate(R.layout.preferences_full_screen_edit_text, null)
        layout.setBackgroundColor(P.colorPrimary)

        val editText = layout.findViewById<EditText>(R.id.text)
        editText.setBody(buildBody(Select.WithoutTimestamps) ?: return null)

        layout.findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.dialog__copy__title)
            setNavigationOnClickListener { dialog.dismiss() }
            inflateMenu(R.menu.copy_dialog_fullscreen)
            setOnMenuItemClickListener listener@{ menuItem ->
                val select = Select::id.find(menuItem.itemId) ?: return@listener false
                val body = buildBody(select) ?: return@listener false
                editText.setBody(body)
                true
            }
        }

        dialog.window?.run {
            setDimAmount(0f)

            val isDark = !ThemeFix.isColorLight(P.colorPrimaryDark)
            if (isDark || Build.VERSION.SDK_INT >= 23) statusBarColor = P.colorPrimaryDark
            if (isDark || Build.VERSION.SDK_INT >= 26) navigationBarColor = P.colorPrimaryDark

            // prevent keyboard from showing, while still allow selecting text
            // apparently you can't just disable editing text
            setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }

        dialog.setView(layout)
        return dialog
    }
}


private fun setClipboard(text: CharSequence) {
    val clipboardManager = applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboardManager?.text = text
}


private data class Body(val text: CharSequence, val selectionStart: Int, val selectionEnd: Int)

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
fun EditText.selectTextCentering(selectionStart: Int, selectionEnd: Int) {
    selectTextSafe(selectionStart - 400, selectionEnd + 400)
    post { selectTextSafe(selectionStart, selectionEnd) }
}


fun EditText.selectTextSafe(selectionStart: Int, selectionEnd: Int) {
    setSelection(selectionStart.coerceIn(text.indices),
                 selectionEnd.coerceIn(text.indices))
}