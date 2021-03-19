// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Utils
import java.util.*

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
            if (value == Status.Init) {
                maxUnfilteredSize = P.lineIncrement
                shouldAddSquiggleOnNewLastLine = false
            }
        }

    private val filtered = ArrayDeque<Line>()
    private val unfiltered = ArrayDeque<Line>()

    private var skipUnfiltered = -1
    private var skipFiltered = -1
    private var skipUnfilteredOffset = -1
    private var skipFilteredOffset = -1

    var maxUnfilteredSize = P.lineIncrement
        private set

    // after reconnecting, in *full sync mode*, we are receiving and adding new lines to buffers.
    // there can be inconsistencies; if some lines were added while we were offline,
    // we can't display them, but can display what's on top and what's on bottom.
    // to resolve this, add a squiggly separator between the old and the new lines ...
    private var shouldAddSquiggleOnNewLastLine = false

    // ... but only add it if the buffer's last line pointer has changed.
    // it would be nice if we can do this separately for visible & invisible lines;
    // however, it doesn't seem to be possible to retrieve the pointer for last *visible* line
    fun updateLastLineInfo(pointer: Long) {
        val bufferReceivedLinesWhileNotSynced = pointer != unfiltered.lastOrNull()?.pointer
        if (bufferReceivedLinesWhileNotSynced) shouldAddSquiggleOnNewLastLine = true
    }

    // it might look like there's a room for optimization here,
    // but these uses very optimized array operations underneath and
    // getting this work faster is probably just not feasible or worth the effort
    fun getCopy(): ArrayList<Line> {
        return ArrayList(if (P.filterLines) filtered else unfiltered).apply {
            add(0, HeaderLine)
            val skip = if (P.filterLines) skipFiltered else skipUnfiltered
            val marker = if (skip >= 0 && size > 0) size - skip else -1
            if (marker > 0) add(marker, MarkerLine)
            while (isNotEmpty() && last() is SquiggleLine) removeLast()
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

    fun addLast(line: Line) {
        if (shouldAddSquiggleOnNewLastLine) {
            shouldAddSquiggleOnNewLastLine = false
            if (status == Status.Init && unfiltered.size > 0) addLast(SquiggleLine())
        }

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

    val descendingFilteredIterator: Iterator<Line> get() = filtered.descendingIterator()


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
        if (skipFilteredOffset >= 0 && skipUnfilteredOffset >= 0 &&
                skipFiltered >= skipFilteredOffset && skipUnfiltered >= skipFilteredOffset) {
            skipFiltered -= skipFilteredOffset
            skipUnfiltered -= skipUnfilteredOffset
        } else {
            skipUnfiltered = 0
            skipFiltered = 0
        }
        skipUnfilteredOffset = -1
        skipFilteredOffset = -1
    }

    fun rememberCurrentSkipsOffset() {
        skipFilteredOffset = skipFiltered
        skipUnfilteredOffset = skipUnfiltered
        if (unfiltered.size > 0) _lastSeenLine = unfiltered.last.pointer
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

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

        unfiltered.descendingIterator().forEach { line ->
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
}


open class FakeLine : Line(
        ++fakePointerCounter, LineSpec.Type.Other,
        timestamp = 0, rawPrefix = "", rawMessage = "",
        nick = null, isVisible = true, isHighlighted = false,
        LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low)

object MarkerLine : FakeLine()              // can have only one per buffer
object HeaderLine : FakeLine()              // can have only one per buffer
class SquiggleLine : FakeLine()             // can have several of these


private const val MAX_C_POINTER_VALUE = 0x200000000000000   // 2⁵⁷

private var fakePointerCounter = MAX_C_POINTER_VALUE


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
