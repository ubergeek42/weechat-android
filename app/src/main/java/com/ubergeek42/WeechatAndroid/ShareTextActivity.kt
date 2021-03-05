package com.ubergeek42.WeechatAndroid

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RelativeLayout
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.upload.preloadThumbnailsForIntent
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.ThemeFix
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.Utils
import org.greenrobot.eventbus.EventBus

class ShareTextActivity : AppCompatActivity(), DialogInterface.OnDismissListener, BufferListClickListener {
    private var dialog: Dialog? = null

    private var uiRecycler: RecyclerView? = null
    private var adapter: BufferListAdapter? = null
    private var uiFilterBar: RelativeLayout? = null
    private var uiFilter: EditText? = null
    private var uiFilterClear: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)   // required for ThemeFix.fixIconAndColor()
        ThemeFix.fixIconAndColor(this)
    }

    override fun onStart() {
        super.onStart()

        if (!EventBus.getDefault().getStickyEvent(StateChangedEvent::class.java).state.contains(RelayService.STATE.LISTED)) {
            Toaster.ErrorToast.show(R.string.error__etc__not_connected)
            finish()
            return
        }

        if (Utils.isAnyOf(intent.action, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            preloadThumbnailsForIntent(intent)

            dialog = Dialog(this, R.style.AlertDialogTheme)
            dialog!!.setContentView(R.layout.bufferlist_share)

            uiRecycler = dialog!!.findViewById(R.id.recycler)
            uiFilterBar = dialog!!.findViewById(R.id.filter_bar)
            uiFilter = dialog!!.findViewById(R.id.bufferlist_filter)
            uiFilterClear = dialog!!.findViewById(R.id.bufferlist_filter_clear)

            adapter = BufferListAdapter()
            uiRecycler!!.adapter = adapter
            uiFilterClear!!.setOnClickListener { uiFilter!!.text = null }
            uiFilter!!.addTextChangedListener(filterTextWatcher)
            uiFilter!!.setText(BufferListAdapter.filterGlobal)

            adapter!!.onBuffersChanged()
            dialog!!.setCanceledOnTouchOutside(true)
            dialog!!.setCancelable(true)
            dialog!!.setOnDismissListener(this)

            if (!P.showBufferFilter) {
                uiFilterBar!!.visibility = View.GONE
                uiRecycler!!.setPadding(0, 0, 0, 0)
            }

            applyColorSchemeToViews()

            dialog!!.show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (dialog == null) return
        dialog!!.setOnDismissListener(null)     // prevent dismiss() from finish()ing the activity
        dialog!!.dismiss()                      // must be called in order to not cause leaks
    }

    // as we are receiving uris now, it's important that we keep all permissions associated with them.
    // while many uris we'll be given for life, some will only last as long as this activity lasts.
    // see flag FLAG_GRANT_READ_URI_PERMISSION and https://stackoverflow.com/a/39898958/1449683
    override fun onBufferClick(pointer: Long) {
        intent.setClass(applicationContext, WeechatActivity::class.java)
        intent.putExtra(Constants.EXTRA_BUFFER_POINTER, pointer)
        startActivity(intent)
        finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        finish()
    }

    private val filterTextWatcher: TextWatcher = object : TextWatcher {
        @MainThread override fun afterTextChanged(a: Editable) { }
        @MainThread override fun beforeTextChanged(arg0: CharSequence, a: Int, b: Int, c: Int) { }
        @MainThread override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            uiFilterClear!!.visibility = if (s.isEmpty()) View.INVISIBLE else View.VISIBLE
            adapter!!.setFilter(s.toString(), false)
            adapter!!.onBuffersChanged()
        }
    }

    private fun applyColorSchemeToViews() {
        uiFilterBar!!.setBackgroundColor(P.colorPrimary)
        uiRecycler!!.setBackgroundColor(P.colorPrimary)
    }
}