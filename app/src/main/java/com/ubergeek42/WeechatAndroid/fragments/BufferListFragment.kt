package com.ubergeek42.WeechatAndroid.fragments

import android.content.Context
import com.ubergeek42.WeechatAndroid.relay.BufferListEye
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.RelativeLayout
import android.widget.EditText
import android.widget.ImageButton
import com.ubergeek42.cats.Cat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.ubergeek42.WeechatAndroid.R
import org.greenrobot.eventbus.EventBus
import com.ubergeek42.WeechatAndroid.service.P
import org.greenrobot.eventbus.Subscribe
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.Hotlist
import android.view.View
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import com.ubergeek42.WeechatAndroid.upload.main
import com.ubergeek42.WeechatAndroid.utils.afterTextChanged
import com.ubergeek42.WeechatAndroid.views.onBufferListFragmentStarted
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

class BufferListFragment : Fragment(), BufferListEye {
    companion object {
        @Root private val kitty: Kitty = Kitty.make("BLF")
    }

    private lateinit var weechatActivity: WeechatActivity
    private lateinit var adapter: BufferListAdapter

    private lateinit var uiRecycler: RecyclerView
    private lateinit var uiFilterBar: RelativeLayout
    private lateinit var uiFilter: EditText
    private lateinit var uiFilterClear: ImageButton

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////// lifecycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Cat override fun onAttach(context: Context) {
        super.onAttach(context)
        weechatActivity = context as WeechatActivity
    }

    @MainThread @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = BufferListAdapter(requireContext())
    }

    @MainThread @Cat override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                               savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bufferlist, container, false)
        uiRecycler = view.findViewById(R.id.recycler)
        uiFilter = view.findViewById(R.id.bufferlist_filter)
        uiFilterClear = view.findViewById(R.id.bufferlist_filter_clear)
        uiFilterBar = view.findViewById(R.id.filter_bar)

        uiRecycler.adapter = adapter

        uiFilterClear.setOnClickListener {
            uiFilter.text = null
        }

        uiFilter.afterTextChanged {
            applyFilter()
            adapter.onBuffersChanged()
        }

        return view
    }

    @MainThread @Cat override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        uiFilterBar.visibility = if (P.showBufferFilter) View.VISIBLE else View.GONE
        applyColorSchemeToViews()
        onBufferListFragmentStarted(this)
    }

    @MainThread @Cat override fun onStop() {
        super.onStop()
        detachFromBufferList()
        EventBus.getDefault().unregister(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// event
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Subscribe(sticky = true) @AnyThread @Cat fun onEvent(event: StateChangedEvent) {
        if (event.state.contains(RelayService.STATE.LISTED)) attachToBufferList()
    }

    ////////////////////////////////////////////////////////////////////////////////////// the juice

    @AnyThread private fun attachToBufferList() {
        BufferList.setBufferListEye(this)
        applyFilter()
        onBuffersChanged()
    }

    @MainThread private fun detachFromBufferList() {
        BufferList.setBufferListEye(null)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Volatile private var hotCount = 0

    // if hot count has changed and > 0, scroll to top. note that if the scroll operation is not
    // posted, it cat try to scroll to the view that was at position 0 before the diff update
    // todo don't update on every change?
    // todo move hotlist updates to the activity
    @AnyThread @Cat override fun onBuffersChanged() {
        adapter.onBuffersChanged()

        val hotCount = Hotlist.getHotCount()
        main {
            if (this.hotCount != hotCount) {
                this.hotCount = hotCount
                if (hotCount > 0) uiRecycler.smoothScrollToPosition(0)
            }
            weechatActivity.updateHotCount(hotCount)
            weechatActivity.onBuffersChanged()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// other
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread private fun applyFilter() {
        val text = uiFilter.text.toString()
        adapter.setFilter(text, true)
        main {
            uiFilterClear.visibility = if (text.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
    }


    private fun applyColorSchemeToViews() {
        uiFilterBar.setBackgroundColor(P.colorPrimary)
        uiRecycler.setBackgroundColor(P.colorPrimary)
    }

    override fun toString() = "BL"
}