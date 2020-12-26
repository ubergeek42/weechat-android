package com.ubergeek42.WeechatAndroid.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.FancyAlertDialogBuilder


abstract class ListDialog : DialogFragment() {
    abstract val adapter: RecyclerView.Adapter<*>?

    var title: CharSequence? = null
        set(text) {
            field = text
            dialog?.setTitle(text)
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = FancyAlertDialogBuilder(requireContext())
                .setTitle(title)
                .create()

        val recyclerView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_list, null) as RecyclerView

        recyclerView.adapter = adapter

        dialog.setView(recyclerView)
        return dialog
    }

    interface Item {
        val text: CharSequence
    }

    abstract class Adapter<T: Item>(
        context: Context,
        private val viewLayoutResource: Int,
        private val textViewResource: Int,
    ) : RecyclerView.Adapter<Adapter<T>.Row>() {
        private val inflater = LayoutInflater.from(context)

        fun interface OnClickListener<T> {
            fun onClick(item: T)
        }

        abstract var items: List<T>
        abstract var onClickListener: OnClickListener<T>

        inner class Row(val view: View) : RecyclerView.ViewHolder(view) {
            lateinit var item: T
            init { view.setOnClickListener { onClickListener.onClick(item) } }
            private val textView: TextView = view.findViewById(textViewResource)

            fun update(item: T) {
                this.item = item
                textView.text = item.text
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Row, position: Int) {
            holder.update(items[position])
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
            return Row(inflater.inflate(viewLayoutResource, parent, false))
        }
    }
}


interface DiffItem<T> {
    fun isSameItem(other: T): Boolean
    fun isSameContents(other: T): Boolean
}


class DiffCallback<T : DiffItem<T>>(
    private val old: List<T>,
    private val new: List<T>,
) : DiffUtil.Callback() {
    override fun getOldListSize() = old.size
    override fun getNewListSize() = new.size
    override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].isSameItem(new[newPos])
    override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos].isSameContents(new[newPos])
}
