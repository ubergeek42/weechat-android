package com.ubergeek42.WeechatAndroid.copypaste

import android.content.Context
import android.text.ClipboardManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.dialogs.FancyAlertDialogBuilder
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.views.LineView
import java.util.*

object Copy {
    @JvmStatic fun showCopyDialog(lineView: LineView): Boolean {
        lineView.showCopyDialog()
        return true
    }
}

fun LineView.showCopyDialog() {
    val context = context
    val line = tag as Line

    val list = ArrayList<CharSequence>()
    if (line.prefixString.isNotEmpty()) list.add(line.ircLikeString)
    list.add(line.messageString)
    list.addAll(urls.map { it.url })

    val dialog = FancyAlertDialogBuilder(context).create()

    val layout = LayoutInflater.from(context).inflate(R.layout.dialog_copy, null) as ViewGroup
    val recyclerView = layout.findViewById<RecyclerView>(R.id.list)
    val title = layout.findViewById<TextView>(R.id.title)

    title.setText(R.string.dialog__copy__title)

    recyclerView.adapter = CopyAdapter(context, list.distinct()) { item ->
        setClipboard(item)
        dialog.dismiss()
    }

    dialog.setView(layout)
    dialog.show()
}


private fun setClipboard(text: CharSequence) {
    val clipboardManager = applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboardManager?.text = text
}