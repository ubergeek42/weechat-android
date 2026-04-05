package com.ubergeek42.WeechatAndroid.copypaste

import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.dialogs.FancyAlertDialogBuilder
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.views.LineView


fun showCopyDialog(lineView: LineView, bufferPointer: Long) {
    Copy(lineView.context, bufferPointer, lineView, lineView.tag as Line)
            .buildCopyDialog()
            .show()
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
            context.setClipboardText(item)
            dialog.dismiss()
        }

        layout.findViewById<ImageButton>(R.id.select_text).setOnClickListener {
            launchCopyActivity(context, bufferPointer, sourceLine.pointer)
            dialog.dismiss()
        }

        dialog.setView(layout)
        return dialog
    }
}


fun Context.setClipboardText(text: CharSequence) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboardManager?.setPrimaryClip(ClipData.newPlainText(text, text))
}

fun Context.getClipboardText(): CharSequence {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboardManager?.primaryClip?.getItemAt(0)?.coerceToText(this) ?: ""
}