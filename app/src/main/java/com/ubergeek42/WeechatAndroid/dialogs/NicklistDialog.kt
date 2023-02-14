package com.ubergeek42.WeechatAndroid.dialogs

import android.content.Context
import android.os.Bundle
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.BufferNicklistEye
import com.ubergeek42.WeechatAndroid.relay.Nick
import com.ubergeek42.WeechatAndroid.service.Events


private const val BUFFER_POINTER = "buffer-pointer"


class NicklistDialog : ListDialog(),
                       BufferNicklistEye {
    override var adapter: NicklistAdapter? = null
    var buffer: Buffer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pointer = requireArguments().getLong(BUFFER_POINTER)
        val buffer = BufferList.findByPointer(pointer)
        if (buffer == null) {
            dismiss()
        } else {
            this.buffer = buffer
            this.title = buffer.shortName
            this.adapter = NicklistAdapter(requireContext()) {
                Events.SendMessageEvent.fireInput(buffer, "/query -noswitch ${it.name}")
                dismiss()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        buffer?.let {
            onNicklistChanged()
            it.setBufferNicklistEye(this)
        }
    }

    override fun onPause() {
        super.onPause()
        buffer?.setBufferNicklistEye(null)
    }


    @AnyThread override fun onNicklistChanged() {
        Weechat.runOnMainThreadASAP {
            val nicks = buffer!!.nicksCopySortedByPrefixAndName
            val count = requireContext().resources.getQuantityString(R.plurals.dialog__nicklist__n_users, nicks.size, nicks.size)
            title = requireContext().getString(R.string.dialog__nicklist__title, buffer!!.shortName, count)
            adapter?.update(nicks)
        }
    }

    companion object {
        private fun newInstance(pointer: Long): NicklistDialog {
            return NicklistDialog().apply {
                arguments = Bundle().also { it.putLong(BUFFER_POINTER, pointer) }
            }
        }

        @JvmStatic fun show(activity: AppCompatActivity, pointer: Long) {
            newInstance(pointer).show(activity.supportFragmentManager, "nicklist")
        }
    }
}


class NicklistAdapter(
    val context: Context,
    override var onClickListener: OnClickListener<VisualNick>,
) : ListDialog.Adapter<NicklistAdapter.VisualNick>(context, R.layout.dialog_copy_line, R.id.text) {
    override var items: List<VisualNick> = emptyList()

    @MainThread fun update(nicks: List<Nick>) {
        val visualNicks = nicks.map(::VisualNick)
        val diff = DiffUtil.calculateDiff(DiffCallback(items, visualNicks), true)
        items = visualNicks
        diff.dispatchUpdatesTo(this)
    }


    inner class VisualNick(nick: Nick) : DiffItem<VisualNick>, ListDialog.Item {
        private val id = nick.pointer
        val name: String = nick.name

        override val text: String = if (nick.away) {
                    context.getString(R.string.dialog__nicklist__user_away, nick.asString())
                } else {
                    nick.asString()
                }

        override fun isSameItem(other: VisualNick) = id == other.id
        override fun isSameContents(other: VisualNick) = text == other.text
    }
}
