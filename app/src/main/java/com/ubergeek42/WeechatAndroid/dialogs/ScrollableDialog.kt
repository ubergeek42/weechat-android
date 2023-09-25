package com.ubergeek42.WeechatAndroid.dialogs

import android.app.Dialog
import android.content.Context
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import com.ubergeek42.WeechatAndroid.R

private class ButtonInfo(
    val title: CharSequence,
    val onClickListener: () -> Unit
)

private class ScrollableDialogInfo(
    var text: CharSequence? = null,
    var title: CharSequence? = null,
    var positiveButton: ButtonInfo? = null,
    var negativeButton: ButtonInfo? = null,
)

interface ScrollableDialogBuilder {
    fun setTitle(title: CharSequence)
    fun setTitle(titleResourceId: Int)
    fun setText(text: CharSequence)
    fun setText(textResourceId: Int)
    fun setPositiveButton(title: CharSequence, onClickListener: () -> Unit)
    fun setPositiveButton(titleResourceId: Int, onClickListener: () -> Unit)
    fun setNegativeButton(title: CharSequence, onClickListener: () -> Unit)
    fun setNegativeButton(titleResourceId: Int, onClickListener: () -> Unit)
}

private class ScrollableDialogBuilderImpl(val context: Context) : ScrollableDialogBuilder {
    val info = ScrollableDialogInfo()
    override fun setTitle(title: CharSequence) {
        info.title = title
    }

    override fun setTitle(titleResourceId: Int) {
        info.title = context.getString(titleResourceId)
    }

    override fun setText(text: CharSequence) {
        info.text = text
    }

    override fun setText(textResourceId: Int) {
        info.text = context.getString(textResourceId)
    }

    override fun setPositiveButton(title: CharSequence, onClickListener: () -> Unit) {
        info.positiveButton = ButtonInfo(title, onClickListener)
    }

    override fun setPositiveButton(titleResourceId: Int, onClickListener: () -> Unit) {
        info.positiveButton = ButtonInfo(context.getString(titleResourceId), onClickListener)
    }

    override fun setNegativeButton(title: CharSequence, onClickListener: () -> Unit) {
        info.negativeButton = ButtonInfo(title, onClickListener)
    }

    override fun setNegativeButton(titleResourceId: Int, onClickListener: () -> Unit) {
        info.negativeButton = ButtonInfo(context.getString(titleResourceId), onClickListener)
    }
}

fun Context.createScrollableDialog(builderBlock: ScrollableDialogBuilder.() -> Unit): Dialog {
    val info = ScrollableDialogBuilderImpl(this).apply(builderBlock).info

    val padding = resources.getDimension(R.dimen.dialog_padding_full).toInt()
    val scrollView = ScrollView(this)
    scrollView.addView(AppCompatTextView(this).apply { text = info.text })
    scrollView.setPadding(padding, padding / 2, padding, 0)

    val builder: AlertDialog.Builder = FancyAlertDialogBuilder(this)
        .setTitle(info.title)
        .setView(scrollView)

    info.positiveButton?.let {
        builder.setPositiveButton(it.title) { _, _ -> it.onClickListener() }
    }

    info.negativeButton?.let {
        builder.setNegativeButton(it.title) { _, _ -> it.onClickListener() }
    }

    return builder.create()
}
