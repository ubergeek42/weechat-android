package com.ubergeek42.WeechatAndroid

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RelativeLayout
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
import com.ubergeek42.WeechatAndroid.utils.afterTextChanged
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import org.greenrobot.eventbus.EventBus

class ShareTextActivity : AppCompatActivity(), BufferListClickListener {
    private var dialog: Dialog? = null

    private var uiRecycler: RecyclerView? = null
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

        if (intent.action.isAnyOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            preloadThumbnailsForIntent(intent)

            dialog = Dialog(this, R.style.AlertDialogTheme).also {
                it.setContentView(R.layout.bufferlist_share)
                it.setCanceledOnTouchOutside(true)
                it.setCancelable(true)
                it.setOnDismissListener { finish() }

                uiRecycler = it.findViewById(R.id.recycler)
                uiFilterBar = it.findViewById(R.id.filter_bar)
                uiFilter = it.findViewById(R.id.bufferlist_filter)
                uiFilterClear = it.findViewById(R.id.bufferlist_filter_clear)
            }

            val adapter = BufferListAdapter()

            uiRecycler?.adapter = adapter
            uiFilterClear?.setOnClickListener { uiFilter?.text = null }

            uiFilter?.run {
                setText(BufferListAdapter.filterGlobal)
                afterTextChanged {
                    uiFilterClear?.visibility = if (it.isEmpty()) View.INVISIBLE else View.VISIBLE
                    adapter.setFilter(it.toString(), false)
                    adapter.onBuffersChanged()
                }
            }

            adapter.onBuffersChanged()

            if (!P.showBufferFilter) {
                uiFilterBar?.visibility = View.GONE
                uiRecycler?.setPadding(0, 0, 0, 0)
            }

            applyColorSchemeToViews()

            dialog?.show()
        }
    }

    override fun onStop() {
        super.onStop()
        dialog?.let {
            it.setOnDismissListener(null)       // prevent dismiss() from finish()ing the activity
            it.dismiss()                        // must be called in order to not cause leaks
        }
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

    private fun applyColorSchemeToViews() {
        uiFilterBar?.setBackgroundColor(P.colorPrimary)
        uiRecycler?.setBackgroundColor(P.colorPrimary)
    }
}