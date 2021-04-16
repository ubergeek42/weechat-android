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
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.preloadThumbnailsForIntent
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.ThemeFix
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.afterTextChanged
import com.ubergeek42.WeechatAndroid.utils.isNotAnyOf

class ShareTextActivity : AppCompatActivity(), BufferListClickListener {
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)   // required for ThemeFix.fixIconAndColor()
        ThemeFix.fixIconAndColor(this)
    }

    override fun onStart() {
        super.onStart()

        if (intent.action.isNotAnyOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            finish()
            return
        }

        val adapter = BufferListAdapter(this).apply {
            val pendingItemCount = onBuffersChanged()

            if (pendingItemCount == 0) {
                val canShowBuffers = P.showBufferFilter && BufferList.buffers.isNotEmpty()
                if (!canShowBuffers) {
                    Toaster.ErrorToast.show(R.string.error__etc__cannot_share_to_empty_buffer_list)
                    finish()
                    return
                }
            }
        }

        preloadThumbnailsForIntent(intent)

        val dialog = Dialog(this, R.style.AlertDialogTheme).apply {
            setContentView(R.layout.bufferlist_share)
            setCanceledOnTouchOutside(true)
            setCancelable(true)
            setOnDismissListener { finish() }
        }

        val uiRecyclerView = dialog.findViewById<RecyclerView>(R.id.recycler)
        val uiFilterBar = dialog.findViewById<RelativeLayout>(R.id.filter_bar)
        val uiFilter = dialog.findViewById<EditText>(R.id.bufferlist_filter)
        val uiFilterClear = dialog.findViewById<ImageButton>(R.id.bufferlist_filter_clear)

        uiRecyclerView.adapter = adapter
        uiFilterClear.setOnClickListener { uiFilter.text = null }

        uiFilter.afterTextChanged {
            uiFilterClear.visibility = if (it.isEmpty()) View.INVISIBLE else View.VISIBLE
            adapter.setFilter(it.toString(), false)
            adapter.onBuffersChanged()
        }

        uiFilter.setText(BufferListAdapter.filterGlobal)

        if (!P.showBufferFilter) {
            uiFilterBar.visibility = View.GONE
            uiRecyclerView.setPadding(0, 0, 0, 0)
        }

        applyColorSchemeToViews(uiFilterBar, uiRecyclerView)

        this.dialog = dialog
        dialog.show()
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

    private fun applyColorSchemeToViews(vararg primaryBackgroundColorViews: View) {
        primaryBackgroundColorViews.forEach { it.setBackgroundColor(P.colorPrimary) }
    }
}