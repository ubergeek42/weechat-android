package com.ubergeek42.WeechatAndroid.copypaste

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.ClipboardManager
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
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

    private fun buildPopupMenu(anchor: View, listener: (Select) -> Unit): PopupMenu {
        return PopupMenu(context, anchor).apply {
            inflate(R.menu.copy_dialog)
            setOnMenuItemClickListener menuListener@{ menuItem ->
                val select = Select::id.find(menuItem.itemId) ?: return@menuListener false
                listener.invoke(select)
                return@menuListener true
            }
        }
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

        layout.findViewById<ImageButton>(R.id.overflow).setOnClickListener { overflow ->
            buildPopupMenu(overflow) { select ->
                buildFullScreenCopyDialog(select)?.show()
                dialog.dismiss()
            }.show()
        }

        dialog.setView(layout)
        return dialog
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buildFullScreenCopyDialog(select: Select): Dialog? {
        val lines = (BufferList.findByPointer(bufferPointer) ?: return null).linesCopy
        val lineTextGetter = select.textGetter

        val body = StringBuilder()
        var selectionStart = -1
        var selectionEnd = -1

        lines.forEach { line ->
            if (line.pointer == sourceLine.pointer) selectionStart = body.length
            body.append(lineTextGetter(line))
            if (line.pointer == sourceLine.pointer) selectionEnd = body.length
            body.append("\n")
        }

        val dialog = AlertDialog.Builder(context, R.style.FullScreenAlertDialogTheme).create()
        val layout = LayoutInflater.from(context)
                .inflate(R.layout.preferences_full_screen_edit_text, null)
        layout.setBackgroundColor(P.colorPrimary)

        layout.findViewById<EditText>(R.id.text).apply {
            setText(body)
            if (selectionStart != -1 && selectionEnd != -1) post {
                requestFocus()
                setTextIsSelectable(true)
                selectTextCentering(selectionStart, selectionEnd)
            }
        }

        layout.findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.dialog__copy__title)
            setNavigationOnClickListener { dialog.dismiss() }
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