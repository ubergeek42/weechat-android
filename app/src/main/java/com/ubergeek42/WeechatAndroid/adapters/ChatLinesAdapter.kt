// Copyright 2012 Keith Johnson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.ubergeek42.WeechatAndroid.adapters

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.R.layout
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.copypaste.showCopyDialog
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferEye
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.relay.LineSpec
import com.ubergeek42.WeechatAndroid.relay.Lines
import com.ubergeek42.WeechatAndroid.relay.MarkerLine
import com.ubergeek42.WeechatAndroid.relay.SquiggleLine
import com.ubergeek42.WeechatAndroid.relay.HeaderLine
import com.ubergeek42.WeechatAndroid.search.Search
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.upload.main
import com.ubergeek42.WeechatAndroid.views.AnimatedRecyclerView
import com.ubergeek42.WeechatAndroid.views.Animation
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.forEachReversedIndexed
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import com.ubergeek42.WeechatAndroid.utils.ulet
import com.ubergeek42.WeechatAndroid.views.LineView
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.WeechatAndroid.views.updateMargins
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.ColorScheme
import java.util.*

class ChatLinesAdapter @MainThread constructor(
    private val uiLines: AnimatedRecyclerView
) : RecyclerView.Adapter<ViewHolder>(), BufferEye {
    @Root private val kitty: Kitty = Kitty.make("ChatLinesAdapter")

    private val inflater = LayoutInflater.from(uiLines.context)

    private var lines = ArrayList<Line>()
    @Volatile private var _lines: List<Line> = ArrayList<Line>()

    init { setHasStableIds(true) }

    var buffer: Buffer? = null
        @MainThread get
        @MainThread @Synchronized set(value) {
            if (field != value) {
                field = value
                kitty.setPrefix(value?.shortName)
            }
        }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// holders
    ////////////////////////////////////////////////////////////////////////////////////////////////


    private inner class Row(view: LineView) : ViewHolder(view) {
        private val lineView = view.apply {
            setOnLongClickListener {
                showCopyDialog(this, buffer?.pointer ?: -1L)
                true
            }
        }

        @MainThread fun update(line: Line) {
            lineView.tag = line
            lineView.setText(line)
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////


    private class ReadMarkerRow(private val view: View) : ViewHolder(view) {
        @MainThread fun update() {
            view.setBackgroundColor(readMarkerColor)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    private class SquiggleRow(private val view: ImageView) : ViewHolder(view) {
        @MainThread fun update() {
            view.setColorFilter(readMarkerColor, PorterDuff.Mode.SRC_IN)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    private inner class HeaderRow(header: View) : ViewHolder(header) {
        private val title: LineView = header.findViewById<LineView>(R.id.title).apply {
            setOnLongClickListener {
                showCopyDialog(this, buffer?.pointer ?: -1)
                true
            }
        }

        private val button: Button = header.findViewById<Button>(R.id.button_more).apply {
            setOnClickListener {
                buffer?.let { buffer ->
                    buffer.requestMoreLines()
                    // instead of calling onLinesListed(), which works,
                    // call the following shortcut to forgo change animation
                    updateButton(buffer.linesStatus)
                }
            }
        }

        @MainThread fun update(line: HeaderLine) {
            updateButton(line.status)
            updateTitle(line)
        }

        @MainThread private fun updateButton(linesStatus: Lines.Status) {
            if (linesStatus === Lines.Status.EverythingFetched) {
                button.visibility = View.GONE
            } else {
                button.visibility = View.VISIBLE
                val canFetchMore = linesStatus === Lines.Status.CanFetchMore
                button.isEnabled = canFetchMore
                button.text = button.context.getString(if (canFetchMore)
                    R.string.ui__button_fetch_more_lines else R.string.ui__button_fetching_lines)
            }
        }

        // don't show the title when fetching lines and only the button is visible --
        // it just doesn't look good when new lines arrive
        @MainThread private fun updateTitle(line: HeaderLine) {
            if (line.spannable.isEmpty() || (itemCount <= 1 && !line.status.ready())) {
                title.visibility = View.GONE
            } else {
                title.visibility = View.VISIBLE
                title.updateMargins(bottom = if (button.visibility == View.GONE) P._4dp.i else 0)
                title.setText(line)
                title.tag = line
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// adapter
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @MainThread override fun getItemViewType(position: Int): Int {
        return when (lines[position]) {
            is HeaderLine -> HEADER_TYPE
            is MarkerLine -> MARKER_TYPE
            is SquiggleLine -> SQUIGGLE_TYPE
            else -> LINE_TYPE
        }
    }

    @MainThread override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            HEADER_TYPE -> HeaderRow(inflater.inflate(layout.more_button, parent, false))
            MARKER_TYPE -> ReadMarkerRow(inflater.inflate(layout.read_marker, parent, false))
            SQUIGGLE_TYPE -> SquiggleRow(inflater.inflate(layout.squiggle, parent, false) as ImageView)
            else -> Row(LineView(parent.context))
        }
    }

    @MainThread override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val line = lines[position]) {
            is HeaderLine -> (holder as HeaderRow).update(line)
            is MarkerLine -> (holder as ReadMarkerRow).update()
            is SquiggleLine -> (holder as SquiggleRow).update()
            else -> (holder as Row).update(line)
        }
    }

    @MainThread override fun getItemCount() = lines.size

    @MainThread override fun getItemId(position: Int) = lines[position].pointer

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get new lines, perform a simple diff and dispatch change notifications to RecyclerView
    // this might be called by multiple threads in rapid succession
    // in case non-main thread calls this before the Runnable that sets `lines` is executed,
    // store the new list in `_lines` so that we can produce a proper diff
    @AnyThread @Synchronized private fun onLinesChanged(animation: Animation) = ulet(buffer) { buffer ->
        val newLines = buffer.getLinesCopy()

        val diffResult = DiffUtil.calculateDiff(DiffCallback(_lines, newLines), false)
        _lines = newLines

        Weechat.runOnMainThreadASAP {
            lines = newLines
            diffResult.dispatchUpdatesTo(this@ChatLinesAdapter)

            uiLines.setAnimation(animation)

            if (uiLines.onBottom) {
                uiLines.scrollToPosition(itemCount - 1)
            } else {
                uiLines.flashScrollbar()
            }

            search?.onLinesChanged(newLines)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////// BufferEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Synchronized override fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        if (numberChanged && buffer != null) {
            onLinesChanged(Animation.Default)
        } else {
            uiLines.setAnimation(Animation.Default)
            notifyItemRangeChanged(0, _lines.size)
        }
    }

    @WorkerThread override fun onLinesListed() {
        onLinesChanged(Animation.NewLinesFetched)
    }

    @AnyThread override fun onLineAdded() {
        onLinesChanged(Animation.LastLineAdded)
    }

    @WorkerThread override fun onTitleChanged() {
        onLinesChanged(Animation.Default)
    }

    @WorkerThread override fun onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread fun loadLinesWithoutAnimation() {
        onLinesChanged(Animation.None)
    }

    @MainThread @Synchronized fun loadLinesSilently() = ulet(buffer) { buffer ->
        val newLines = buffer.getLinesCopy()
        _lines = newLines
        lines = newLines
    }

    // run scrolling slightly delayed so that stuff on current thread doesn't get in the way
    @MainThread fun scrollToHotLineIfNeeded() {
        when (val idx = findHotLine()) {
            HOT_LINE_NOT_PRESENT -> return
            HOT_LINE_LOST -> Toaster.ShortToast.show(R.string.error__etc__hot_line_lost)
            else -> main(100) { uiLines.smoothScrollToPositionAfterAnimation(idx) }
        }
    }

    private fun findHotLine(): Int {
        var skip = buffer?.hotCount ?: 0
        if (skip == 0) return HOT_LINE_NOT_PRESENT

        lines.forEachReversedIndexed { index, line ->
            if (line.notify.isAnyOf(LineSpec.NotifyLevel.Highlight,
                                    LineSpec.NotifyLevel.Private)) {
                if (--skip == 0) return index
            }
        }

        return HOT_LINE_LOST
    }

    @MainThread fun findPositionByPointer(pointer: Long): Int {
        lines.forEachIndexed { index, line ->
            if (line.pointer == pointer) return index
        }
        return -1
    }

    var search: Search? = null
        @MainThread set(value) {
            field = value
            value?.onLinesChanged(lines)
        }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private inner class DiffCallback(
        private val oldLines: List<Line>,
        private val newLines: List<Line>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldLines.size
        override fun getNewListSize() = newLines.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldLines[oldItemPosition].pointer == newLines[newItemPosition].pointer
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            if (newItemPosition == 0) {
                return oldLines[oldItemPosition] == newLines[newItemPosition]
            }
            return true
        }
    }
}


private const val HEADER_TYPE = -1
private const val LINE_TYPE = 0
private const val MARKER_TYPE = 1
private const val SQUIGGLE_TYPE = 2


private const val HOT_LINE_LOST = -1
private const val HOT_LINE_NOT_PRESENT = -3


private val readMarkerColor get() = ColorScheme.get().chat_read_marker[0].solidColor