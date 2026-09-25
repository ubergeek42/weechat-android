package com.ubergeek42.WeechatAndroid.copypaste

import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.dialogs.FancyAlertDialogBuilder
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.relay.getUrls


fun Context.showCopyDialog(line: Line, bufferPointer: Long) {
    val strings = mutableListOf<String>().apply {
        if (line.getPrefixString().isNotEmpty()) add(line.getIrcLikeString())
        add(line.getMessageString())
        addAll(line.getMessageSpanned().getUrls())
    }.distinct()

    buildCopyDialog(strings, bufferPointer, line.pointer).show()
}


fun Context.showCopyDialog(charSequence: CharSequence, bufferPointer: Long, linePointer: Long) {
    val strings = mutableListOf<String>().apply {
        add(charSequence.toString())
        (charSequence as? Spanned)?.let { addAll(it.getUrls()) }
    }.distinct()

    buildCopyDialog(strings, bufferPointer, linePointer).show()
}

fun Context.buildCopyDialog(strings: List<String>, bufferPointer: Long, sourceLinePointer: Long): Dialog {
    val dialog = FancyAlertDialogBuilder(this).create()
    val layout = LayoutInflater.from(this).inflate(R.layout.dialog_copy, null) as ViewGroup

    layout.findViewById<TextView>(R.id.title).setText(R.string.dialog__copy__title)

    layout.findViewById<RecyclerView>(R.id.list).adapter =
        CopyAdapter(this, strings) { item ->
            setClipboardText(item)
            dialog.dismiss()
        }

    layout.findViewById<ImageButton>(R.id.select_text).setOnClickListener {
        launchCopyActivity(this, bufferPointer, sourceLinePointer)
        dialog.dismiss()
    }

    dialog.setView(layout)
    return dialog
}


fun Context.setClipboardText(text: CharSequence) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboardManager?.setPrimaryClip(ClipData.newPlainText(text, text))
}

fun Context.getClipboardText(): CharSequence {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboardManager?.primaryClip?.getItemAt(0)?.coerceToText(this) ?: ""
}