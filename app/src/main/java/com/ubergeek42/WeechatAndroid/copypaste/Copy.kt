package com.ubergeek42.WeechatAndroid.copypaste

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.ClipboardManager
import android.view.LayoutInflater
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


private enum class Select(val id: Int, val textGetter: (Line) -> String) {
    WithTimestamps(R.id.menu_select_with_timestamps, Line::timestampedIrcLikeString),
    WithoutTimestamps(R.id.menu_select_without_timestamps, Line::ircLikeString),
    MessagesOnly(R.id.menu_select_messages_only, Line::messageString),
}


fun showCopyDialog(lineView: LineView, bufferPointer: Long) {
    val context = lineView.context
    val line = lineView.tag as Line

    val list = ArrayList<CharSequence>()
    if (line.prefixString.isNotEmpty()) list.add(line.ircLikeString)
    list.add(line.messageString)
    list.addAll(lineView.urls.map { it.url })

    val dialog = FancyAlertDialogBuilder(context).create()

    val layout = LayoutInflater.from(context).inflate(R.layout.dialog_copy, null) as ViewGroup
    val recyclerView = layout.findViewById<RecyclerView>(R.id.list)
    val title = layout.findViewById<TextView>(R.id.title)
    val overflow = layout.findViewById<ImageButton>(R.id.overflow)

    title.setText(R.string.dialog__copy__title)

    overflow.setOnClickListener {
        PopupMenu(context, it).apply {
            inflate(R.menu.copy_dialog)
            setOnMenuItemClickListener menuListener@{ menuItem ->
                val select = Select::id.find(menuItem.itemId) ?: return@menuListener false
                createFullScreenCopyDialog(context, bufferPointer, line.pointer, select).show()
                dialog.dismiss()
                return@menuListener true
            }
        }.show()
    }

    recyclerView.adapter = CopyAdapter(context, list.distinct()) { item ->
        setClipboard(item)
        dialog.dismiss()
    }

    dialog.setView(layout)
    dialog.show()
}


private fun createFullScreenCopyDialog(context: Context, bufferPointer: Long,
                                       currentLinePointer: Long, select: Select): Dialog {
    val buffer = BufferList.findByPointer(bufferPointer)
    val lines = buffer!!.linesCopy

    val getter = select.textGetter
    val body = StringBuilder()

    var start = -1
    var end = -1

    lines.forEach { line ->
        if (line.pointer == currentLinePointer) start = body.length
        body.append(getter(line)).append("\n")
        if (line.pointer == currentLinePointer) end = body.length
    }

    val dialog = AlertDialog.Builder(context, R.style.FullScreenAlertDialogTheme).create()

    val contents = LayoutInflater.from(context)
            .inflate(R.layout.preferences_full_screen_edit_text, null).apply {
                setBackgroundColor(P.colorPrimary)
                dialog.setView(this)
            }

    contents.findViewById<EditText>(R.id.text).apply {
        setText(body)
        if (start != -1 && end != -1) post {
            requestFocus()
            setTextIsSelectable(true)
            setSelection(start, end)
        }
    }

    contents.findViewById<Toolbar>(R.id.toolbar).apply {
        setTitle(R.string.dialog__copy__title)
        setNavigationOnClickListener { dialog.dismiss() }
    }

    dialog.window?.run {
        setDimAmount(0f)
        val isDark = !ThemeFix.isColorLight(P.colorPrimaryDark)
        if (isDark || Build.VERSION.SDK_INT >= 23) statusBarColor = P.colorPrimaryDark
        if (isDark || Build.VERSION.SDK_INT >= 26) navigationBarColor = P.colorPrimaryDark

        setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    return dialog
}


private fun setClipboard(text: CharSequence) {
    val clipboardManager = applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboardManager?.text = text
}