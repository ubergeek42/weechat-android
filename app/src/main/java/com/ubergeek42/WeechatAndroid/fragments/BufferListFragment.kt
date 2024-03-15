package com.ubergeek42.WeechatAndroid.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SuppressedLinearLayoutManager
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter
import com.ubergeek42.WeechatAndroid.databinding.BufferlistBinding
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.BufferListEye
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.upload.main
import com.ubergeek42.WeechatAndroid.utils.afterTextChanged
import com.ubergeek42.WeechatAndroid.views.BufferListFragmentFullScreenController
import com.ubergeek42.WeechatAndroid.views.FULL_SCREEN_DRAWER_ENABLED
import com.ubergeek42.WeechatAndroid.views.FullScreenDrawerLinearLayoutManager
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScrollCentering
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class BufferListFragment : Fragment(), BufferListEye {
    companion object {
        @Root private val kitty: Kitty = Kitty.make("BLF")
    }

    private lateinit var weechatActivity: WeechatActivity
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: BufferListAdapter

    lateinit var ui: BufferlistBinding

    init { BufferListFragmentFullScreenController(this).observeLifecycle() }

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
                                               savedInstanceState: Bundle?): View {
        ui = BufferlistBinding.inflate(inflater)

        layoutManager = if (FULL_SCREEN_DRAWER_ENABLED) {
            FullScreenDrawerLinearLayoutManager(requireContext(), ui.bufferList, adapter)
        } else {
            SuppressedLinearLayoutManager(requireContext())
        }

        ui.bufferList.layoutManager = layoutManager
        ui.bufferList.adapter = adapter
        ui.bufferList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                showHideArrows()
            }
        })

        ui.arrowUp.setOnClickListener { scrollUpToNextHotBuffer() }
        ui.arrowDown.setOnClickListener { scrollDownToNextHotBuffer() }

        ui.filterClear.setOnClickListener {
            ui.filterInput.text = null
        }

        ui.filterInput.afterTextChanged {
            applyFilter()
            adapter.onBuffersChanged()
        }

        return ui.root
    }

    @MainThread @Cat override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        ui.filterInput.visibility = if (P.showBufferFilter) View.VISIBLE else View.GONE
        applyColorSchemeToViews()
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
        BufferList.bufferListEye = this
        applyFilter()
        onBuffersChanged()
    }

    @MainThread private fun detachFromBufferList() {
        BufferList.bufferListEye = null
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

        val hotCount = BufferList.totalHotMessageCount
        main {
            showHideArrows()
            if (this.hotCount != hotCount) {
                this.hotCount = hotCount
                if (hotCount > 0) ui.bufferList.smoothScrollToPosition(0)
            }
            weechatActivity.updateHotCount(hotCount)
            weechatActivity.onBuffersChanged()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// other
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun findNextHotBufferPositionAboveVisibleBuffersOrNull(): Int? {
        val firstVisibleBufferPosition = layoutManager.findFirstVisibleItemPosition()
        val positionsToSearch = (firstVisibleBufferPosition - 1) downTo 0
        return adapter.findNextHotBufferPositionOrNull(positionsToSearch)
    }

    private fun findNextHotBufferPositionBelowVisibleBuffersOrNull(): Int? {
        val lastBufferPosition = adapter.itemCount - 1
        val lastVisibleBufferPosition = layoutManager.findLastVisibleItemPosition()
        val positionsToSearch = (lastVisibleBufferPosition + 1)..lastBufferPosition
        return adapter.findNextHotBufferPositionOrNull(positionsToSearch)
    }

    private fun showHideArrows() {
        val animate = weechatActivity.isBufferListVisible()
        val showArrowUp = findNextHotBufferPositionAboveVisibleBuffersOrNull() != null
        val showArrowDown = findNextHotBufferPositionBelowVisibleBuffersOrNull() != null
        if (showArrowUp) ui.arrowUp.show(animate) else ui.arrowUp.hide(animate)
        if (showArrowDown) ui.arrowDown.show(animate) else ui.arrowDown.hide(animate)
    }

    private fun scrollUpToNextHotBuffer() {
        findNextHotBufferPositionAboveVisibleBuffersOrNull()?.let { position ->
            ui.bufferList.jumpThenSmoothScrollCentering(position)
        }
    }

    private fun scrollDownToNextHotBuffer() {
        findNextHotBufferPositionBelowVisibleBuffersOrNull()?.let { position ->
            ui.bufferList.jumpThenSmoothScrollCentering(position)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread private fun applyFilter() {
        val text = ui.filterInput.text.toString()
        adapter.setFilter(text, true)
        main {
            ui.filterClear.visibility = if (text.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
    }


    private fun applyColorSchemeToViews() {
        requireView().setBackgroundColor(P.colorPrimary)
    }

    override fun toString() = "BL"
}