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

import android.content.Context
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter.VisualBuffer
import com.ubergeek42.WeechatAndroid.databinding.BufferlistItemBinding
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.BufferSpec
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.main
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_NUMBER
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_OTHER_MESSAGES_THEN_BY_NUMBER
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SORT_BUFFER_LIST_BY_NUMBER
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.util.Collections


class BufferListAdapter(
    val context: Context
) : RecyclerView.Adapter<ViewHolder>() {
    private val inflater = LayoutInflater.from(context)

    private var buffers = ArrayList<VisualBuffer>()

    private var filterLowerCase = ""
    private var filterUpperCase = ""

    init { setHasStableIds(true) }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////// VH
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class Row @MainThread constructor(view: View) : ViewHolder(view), View.OnClickListener {
        private var pointer: Long = 0

        private val ui = BufferlistItemBinding.bind(view)

        init { view.setOnClickListener(this) }

        @MainThread fun update(buffer: VisualBuffer) {
            pointer = buffer.pointer
            val unreads = buffer.unreads
            val highlights = buffer.highlights

            val important = highlights > 0 || unreads > 0 && buffer.type == BufferSpec.Type.Private
            ui.container.setBackgroundResource(if (important) buffer.type.hotColorRes else buffer.type.colorRes)
            ui.openIndicator.visibility = if (buffer.isOpen) View.VISIBLE else View.GONE
            ui.buffer.text = buffer.printable

            fun TextView.applyNumber(number: Int) {
                if (number > 0) {
                    text = number.toString()
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            ui.hotNumber.applyNumber(highlights)
            ui.warmNumber.applyNumber(unreads)
        }

        @MainThread override fun onClick(v: View) {
            (Utils.getActivity(v) as BufferListClickListener).onBufferClick(pointer)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////// adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return Row(inflater.inflate(R.layout.bufferlist_item, parent, false))
    }

    @MainThread override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder as Row).update(buffers[position])
    }

    @MainThread override fun getItemId(position: Int) = buffers[position].pointer

    @MainThread override fun getItemCount() = buffers.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val updateLock = Any()
    private var updateStep = 0

    @AnyThread @Synchronized fun onBuffersChanged(): Int {
        val thisUpdateStep = synchronized (updateLock) { ++updateStep }

        val newBuffers = ArrayList<VisualBuffer>()

        // this method must not call any synchronized methods of Buffer as this could result in a
        // deadlock (worker thread e: Buffer.addLine() (locks BufferA) -> this.onBuffersChanged()
        // (waiting for main to release this) vs. main thread: onBuffersChanged() (locks this) ->
        // iteration on Buffers: (waiting for e to release BufferA). todo: resolve this gracefully
        for (buffer in BufferList.buffers) {
            if (buffer.type == BufferSpec.Type.HardHidden) continue
            if (!buffer.fullName.lowercase().contains(filterLowerCase)
                    && !buffer.fullName.uppercase().contains(filterUpperCase)) continue
            if (filterLowerCase.isEmpty()) {
                if (P.hideHiddenBuffers && buffer.hidden
                        && buffer.highlights == 0
                        && !(buffer.type == BufferSpec.Type.Private && buffer.unreads != 0)) continue
                if (P.filterBuffers && buffer.type == BufferSpec.Type.Other
                        && buffer.highlights == 0 && buffer.unreads == 0) continue
            }
            newBuffers.add(VisualBuffer.fromBuffer(buffer))
        }

        val bufferComparator = when (P.sortBufferList) {
            PREF_SORT_BUFFER_LIST_BY_NUMBER -> sortByNumberComparator
            PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_NUMBER -> sortByHotMessagesThenByNumberComparator
            PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_OTHER_MESSAGES_THEN_BY_NUMBER -> sortByHotMessagesThenByOtherMessagesThenByNumberComparator
            else -> sortByNumberComparator
        }

        Collections.sort(newBuffers, bufferComparator)

        val diffResult = DiffUtil.calculateDiff(DiffCallback(buffers, newBuffers), false)

        main {
            synchronized (updateLock) {
                if (thisUpdateStep != updateStep) return@main
                buffers = newBuffers
            }

            diffResult.dispatchUpdatesTo(this@BufferListAdapter)
        }

        return newBuffers.size
    }

    @AnyThread @Synchronized fun setFilter(s: String, global: Boolean) {
        if (global) filterGlobal = s

        filterLowerCase = s.lowercase()
        filterUpperCase = s.uppercase()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////// Diff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class VisualBuffer constructor(
        val printable: Spannable?,
        val isOpen: Boolean,
        val highlights: Int,
        val unreads: Int,
        val type: BufferSpec.Type,
        val number: Int,
        val pointer: Long,
    ) {
        companion object {
            fun fromBuffer(buffer: Buffer) = VisualBuffer(
                    isOpen = buffer.isOpen,
                    printable = buffer.printable,
                    highlights = buffer.highlights,
                    unreads = buffer.unreads,
                    type = buffer.type,
                    number = buffer.number,
                    pointer = buffer.pointer,
            )
        }
    }

    private class DiffCallback constructor(
        private val oldBuffers: ArrayList<VisualBuffer>,
        private val newBuffers: ArrayList<VisualBuffer>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldBuffers.size
        override fun getNewListSize() = newBuffers.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                =  oldBuffers[oldItemPosition].pointer == newBuffers[newItemPosition].pointer
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldBuffers[oldItemPosition]
            val new = newBuffers[newItemPosition]
            return old.printable == new.printable
                    && old.isOpen == new.isOpen
                    && old.highlights == new.highlights
                    && old.unreads == new.unreads
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////// Etc

    @MainThread fun findNextHotBufferPositionOrNull(positionsToSearch: IntProgression) =
        positionsToSearch.firstOrNull { position ->
            val buffer = buffers.getOrElse(position) { return@firstOrNull false }
            buffer.highlights > 0 || (buffer.type == BufferSpec.Type.Private && buffer.unreads != 0)
        }

    @MainThread fun findAllHotBufferIds() =
        buffers.mapNotNull { buffer ->
            val hot = buffer.highlights > 0 || (buffer.type == BufferSpec.Type.Private && buffer.unreads != 0)
            if (hot) buffer.pointer else null
        }

    // Returns -1 if not found
    @MainThread fun findPositionByBufferId(id: Long): Int = buffers.indexOfFirst { it.pointer == id }

    companion object {
        @Root private val kitty: Kitty = Kitty.make()

        var filterGlobal = ""
    }
}


private val sortByNumberComparator = Comparator<VisualBuffer> { left, right ->
    left.number - right.number
}

private val sortByHotMessagesThenByNumberComparator = Comparator<VisualBuffer> { left, right ->
    val highlightDiff = right.highlights - left.highlights
    if (highlightDiff != 0) return@Comparator highlightDiff

    val pmLeft = if (left.type == BufferSpec.Type.Private) left.unreads else 0
    val pmRight = if (right.type == BufferSpec.Type.Private) right.unreads else 0
    val pmDiff = pmRight - pmLeft
    if (pmDiff != 0) return@Comparator pmDiff

    left.number - right.number
}


private val sortByHotMessagesThenByOtherMessagesThenByNumberComparator = Comparator<VisualBuffer> { left, right ->
    val highlightDiff = right.highlights - left.highlights
    if (highlightDiff != 0) return@Comparator highlightDiff

    val pmLeft = if (left.type == BufferSpec.Type.Private) left.unreads else 0
    val pmRight = if (right.type == BufferSpec.Type.Private) right.unreads else 0
    val pmDiff = pmRight - pmLeft
    if (pmDiff != 0) return@Comparator pmDiff

    val unreadsDiff = right.unreads - left.unreads
    if (unreadsDiff != 0) return@Comparator unreadsDiff

    left.number - right.number
}