// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.text.SpannableString
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Linkify
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.invalidatableLazy
import com.ubergeek42.WeechatAndroid.utils.replaceFirstWith
import com.ubergeek42.weechat.Color
import kotlin.properties.Delegates.observable

// this class is supposed to be synchronized by Buffer
class Lines {
    enum class Status {
        Init,
        Fetching,
        CanFetchMore,
        EverythingFetched;

        // true if Lines contain all necessary line information and read marker stuff
        // note that the number of lines can be zero and read markers can be not preset
        fun ready() = this == CanFetchMore || this == EverythingFetched
    }

    @Volatile var status = Status.Init
        set(value) {
            field = value
            headerLineDelegate.invalidate()
            if (value == Status.Init) {
                maxUnfilteredSize = P.lineIncrement
            }
        }

    private val filtered = ArrayDeque<Line>()
    private val unfiltered = ArrayDeque<Line>()

    private var skipUnfiltered = -1
    private var skipFiltered = -1

    var maxUnfilteredSize = P.lineIncrement
        private set

    // After reconnecting, we start receiving new lines.
    // However, we can't just add these lines to the existing lines,
    // as WeeChat may have received more lines while we were not connected to it.
    //
    // To work around the issue, we add a squiggly line between the old lines and the new ones,
    // unless we can tell if the last line on the server is the same as we have.
    // Note that the last *visible* pointer in WeeChat is determined by looking at
    // a limited amount of last lines, and thus might be absent.
    //
    // Note that the visibility of lines may change.
    // While it may seem that this can affect visibility of the nearby squiggle lines,
    // at the point when this method is called we can (almost) reliably tell
    // if the *visible* lines are continuous or not,
    // so I think that the visibility of the squiggle line will remain correct.
    //
    // Also note that we do note consider here the scenario
    // where the last visible line becomes invisible in WeeChat.
    // In this case `lastVisiblePointerServer` would point to a line that we have,
    // but it would not be `lastVisiblePointer`.
    fun updateLastLineInfo(lastPointerServer: Long?, lastVisiblePointerServer: Long?) {
        val lastPointer = unfiltered.lastOrNull()?.pointer
        val lastVisiblePointer = filtered.lastOrNull()?.pointer

        if (lastPointer != null && lastPointerServer != lastPointer) {
            val squiggleIsVisible = lastVisiblePointerServer != lastVisiblePointer
            val squiggleLine = SquiggleLine(isVisible = squiggleIsVisible)
            addLast(squiggleLine)
        }
    }

    // it might look like there's a room for optimization here,
    // but these uses very optimized array operations underneath and
    // getting this work faster is probably just not feasible or worth the effort
    fun getCopy(): ArrayList<Line> {
        return ArrayList(if (P.filterLines) filtered else unfiltered).apply {
            add(0, headerLine)
            val skip = if (P.filterLines) skipFiltered else skipUnfiltered
            val marker = if (skip >= 0 && size > 0) size - skip else -1
            if (marker > 0) add(marker, MarkerLine)

            while (isNotEmpty() && first() is SquiggleLine) removeFirst()
            while (isNotEmpty() && last() is SquiggleLine) removeLast()
            // TODO remove consecutive squiggles?
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines

    fun replaceLines(lines: Collection<Line>) {
        if (status != Status.Fetching) return
        unfiltered.clear()
        filtered.clear()
        unfiltered.addAll(lines)
        for (line in lines) {
            if (line.isVisible) filtered.add(line)
        }
    }

    fun replaceLine(line: Line) {
        unfiltered.replaceFirstWith(line, predicate = { it.pointer == line.pointer })
        filtered.clear()
        unfiltered.forEach { line -> if (line.isVisible) filtered.add(line) }
        setSkipsUsingPointer()
    }

    fun addLast(line: Line) {
        val unfilteredSize = unfiltered.size
        val maxUnfilteredSize = maxUnfilteredSize
        val shouldRemoveFirstLine = unfilteredSize == maxUnfilteredSize

        if (shouldRemoveFirstLine) {
            if (unfiltered.removeFirst().isVisible) filtered.removeFirst()
        }

        unfiltered.addLast(line)
        if (line.isVisible) filtered.addLast(line)

        if (status == Status.Fetching) return

        if (skipFiltered >= 0 && line.isVisible) skipFiltered++
        if (skipUnfiltered >= 0) skipUnfiltered++

        if (status == Status.Init) return

        if (shouldRemoveFirstLine) {
            if (status != Status.CanFetchMore) {
                if (!status.ready()) setSkipsUsingPointer()
                status = Status.CanFetchMore
            }
        } else if (unfilteredSize + 1 == maxUnfilteredSize) {
            if (status != Status.EverythingFetched) {
                if (!status.ready()) setSkipsUsingPointer()
                status = Status.EverythingFetched
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun onMoreLinesRequested(newSize: Int) {
        if (status != Status.Init) maxUnfilteredSize = newSize
        status = Status.Fetching
    }

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines
    fun onLinesListed() {
        if (status != Status.Fetching) return
        status = if (unfiltered.size == maxUnfilteredSize)
                Status.CanFetchMore else Status.EverythingFetched
        setSkipsUsingPointer()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    val namesThatSpokeLast: Iterator<String> = iterator {
        filtered.asReversed().forEach { line ->
            if (line.type === LineSpec.Type.IncomingMessage) {
                line.nick?.let { yield(it) }
            }
        }
    }

    fun invalidateSpannables() {
        unfiltered.forEach { it.invalidateSpannable() }
    }

    // process lines that are gre going to be displayed, backwards, on a background thread pool.
    // this method gets called after line filter change, so it does get to process all needed lines
    fun ensureSpannables() {
        val target = if (P.filterLines) filtered else unfiltered
        val snapshot = target.toTypedArray()
        Utils.runInBackground { for (i in snapshot.indices.reversed()) snapshot[i].ensureSpannable() }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun moveReadMarkerToEnd() {
        skipUnfiltered = 0
        skipFiltered = 0

        if (unfiltered.isNotEmpty()) _lastSeenLine = unfiltered.last().pointer
    }

    private var _lastSeenLine = -1L
    var lastSeenLine: Long
        get() = _lastSeenLine
        set(pointer) {
            if (!status.ready()) {
                _lastSeenLine = pointer
                setSkipsUsingPointer()
            }
        }

    private fun setSkipsUsingPointer() {
        var indexFiltered = 0
        var indexUnfiltered = 0

        unfiltered.asReversed().forEach { line ->
            if (line.pointer == _lastSeenLine) {
                skipFiltered = indexFiltered
                skipUnfiltered = indexUnfiltered
                return
            }

            indexUnfiltered++
            if (line.isVisible) indexFiltered++
        }

        skipFiltered = -1
        skipUnfiltered = -1
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    var title: String by observable("") { _, _, _ -> headerLineDelegate.invalidate() }

    private var headerLineDelegate = invalidatableLazy { HeaderLine.make(title, status) }
    private val headerLine by headerLineDelegate
}


private const val MAX_C_POINTER_VALUE = 0x200000000000000       // 2⁵⁷
private var fakePointerCounter = MAX_C_POINTER_VALUE
private val TITLE_LINE_POINTER = ++fakePointerCounter


open class FakeLine(pointer: Long, isVisible: Boolean = false) : Line(
        pointer, LineSpec.Type.Other,
        timestamp = 0, rawPrefix = "", rawMessage = "",
        nick = null, isVisible = isVisible, isHighlighted = false,
        LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low)


object MarkerLine : FakeLine(++fakePointerCounter)  // can have only one per buffer
class SquiggleLine(pointer: Long = ++fakePointerCounter, isVisible: Boolean = false) : FakeLine(pointer, isVisible)  // can have several of these


class HeaderLine(
    override val messageString : String,
    override val spannable: SpannableString,
    val status: Lines.Status,
) : FakeLine(TITLE_LINE_POINTER) {
    companion object {
        fun make(data: String, status: Lines.Status): HeaderLine {
            val title = Color.stripEverything(data)
            val spannable = SpannableString(title).also { Linkify.linkify(it) }
            return HeaderLine(title, spannable, status)
        }
    }
}

//    private void setSkipsUsingHotlist(int h, int u, int o) {
//        Iterator<Line> it = unfiltered.descendingIterator();
//        int hu = h + u;
//        int idx_f = 0, idx_u = 0;
//        skipFiltered = skipUnfiltered = -1;
//        while (it.hasNext()) {
//            Line line = it.next();
//            if (line.highlighted || (line.visible && line.type == Line.LINE_MESSAGE)) hu--;
//            else if (line.visible && line.type == Line.LINE_OTHER) o--;
//            if (hu == -2 || (hu < 0 && o < 0)) {
//                skipFiltered = idx_f;
//                skipUnfiltered = idx_u;
//                return;
//            }
//            idx_u++;
//            if (line.visible) idx_f++;
//        }
//    }
