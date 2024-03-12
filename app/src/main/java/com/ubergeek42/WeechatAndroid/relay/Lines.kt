// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.text.SpannableString
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Linkify
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.invalidatableLazy
import com.ubergeek42.weechat.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
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
                shouldAddSquiggleOnNewLine = false
                shouldAddSquiggleOnNewVisibleLine = false
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

    private var dateOfLastUnfilteredLine: LocalDate? = null
    private var dateOfLastFilteredLine: LocalDate? = null

    private var lastOldDate: LocalDate? = null
    private lateinit var lastNewDate: LocalDate
    private lateinit var lastDateLine: Line

    // after reconnecting, in *full sync mode*, we are receiving and adding new lines to buffers.
    // there can be inconsistencies; if some lines were added while we were offline,
    // we can't display them, but can display what's on top and what's on bottom.
    // to resolve this, add a squiggly separator between the old and the new lines ...
    private var shouldAddSquiggleOnNewLine = false
    private var shouldAddSquiggleOnNewVisibleLine = false


    // ... but only add it if the buffer's last line pointer has changed.
    // do it separately for visible and invisible lines; note, however,
    // that the list of lines is likely incomplete, and the pointer to the last line may be invalid

    // also note that some lines might have changed visibility due to e.g. smart_filter;
    // but keeping them hidden would not create an inconsistency due to their nature
    fun updateLastLineInfo(lastPointerServer: Long?, lastVisiblePointerServer: Long?) {
        val lastPointer = unfiltered.lastOrNull()?.pointer
        val lastVisiblePointer = filtered.lastOrNull()?.pointer

        if (lastPointerServer != lastPointer) shouldAddSquiggleOnNewLine = true
        if (shouldAddSquiggleOnNewLine && lastVisiblePointerServer != lastVisiblePointer)
                    shouldAddSquiggleOnNewVisibleLine = true
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
            while (isNotEmpty() && last() is SquiggleLine) removeLast()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines

    fun replaceLines(lines: Collection<Line>, displayDayChange: Boolean) {
        if (status != Status.Fetching) return
        unfiltered.clear()
        filtered.clear()
        dateOfLastUnfilteredLine = null
        dateOfLastFilteredLine = null

        for (line in lines) {
            addLast(line, displayDayChange)
        }
    }

    private fun makeDateChangeLine(oldDate: LocalDate?, newDate: LocalDate) : Line {
        val tstamp = newDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        var msg = newDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        if (oldDate != null && oldDate.plusDays(1) != newDate) {
            msg += " (" +
                   oldDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
                   ")"
        }
        msg += " --"

        var datePointer = MAX_C_POINTER_VALUE shl 2 + newDate.getYear() shl 32
                          + newDate.getDayOfYear() shl 20
        if (oldDate != null) {
            datePointer += oldDate.getYear() shl 16 + oldDate.getDayOfYear()
        }
        return Line(datePointer, LineSpec.Type.Other, tstamp, "--", msg,
                    nick = null, isVisible = true, isHighlighted = false,
                    LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low)
    }

    private fun obtainDateChangeLine(oldDate: LocalDate?, newDate: LocalDate) : Line {
        if (oldDate == null || lastOldDate != oldDate || lastNewDate != newDate) {
            lastDateLine = makeDateChangeLine(oldDate, newDate)
            lastOldDate = oldDate
            lastNewDate = newDate
        }
        return lastDateLine
    }

    fun addLast(line: Line, displayDayChange: Boolean) {
        if(displayDayChange) {
                val newDate = Instant.ofEpochSecond(line.timestamp / 1000)
                              .atZone(ZoneId.systemDefault())
                              .toLocalDate()

                if (dateOfLastUnfilteredLine != newDate) {
                    unfiltered.addLast(obtainDateChangeLine(dateOfLastUnfilteredLine, newDate))
                    dateOfLastUnfilteredLine = newDate
                    skipUnfiltered++
                }

                if (line.isVisible && dateOfLastFilteredLine != newDate) {
                    filtered.addLast(obtainDateChangeLine(dateOfLastFilteredLine, newDate))
                    dateOfLastFilteredLine = newDate
                    skipFiltered++
                }
        }

        if (shouldAddSquiggleOnNewLine) {
            shouldAddSquiggleOnNewLine = false
            if (status == Status.Init && unfiltered.size > 0)
                addLast(SquiggleLine(), false)   // invisible
        }

        if (shouldAddSquiggleOnNewVisibleLine && line.isVisible) {
            shouldAddSquiggleOnNewVisibleLine = false
            if (status == Status.Init && filtered.size > 0) {
                // “unhide” the squiggly line added above. as it's hidden,
                // the size of visible lines is surely less than maximum
                unfiltered.reversed().firstOrNull { it is SquiggleLine }?.let {
                    filtered.addLast(it)
                    if (skipFiltered >= 0) skipFiltered++
                }
            }
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    var title: String by observable("") { _, _, _ -> headerLineDelegate.invalidate() }

    private var headerLineDelegate = invalidatableLazy { HeaderLine.make(title, status) }
    private val headerLine by headerLineDelegate
}


private const val MAX_C_POINTER_VALUE = 0x200000000000000       // 2⁵⁷
private var fakePointerCounter = MAX_C_POINTER_VALUE
private val TITLE_LINE_POINTER = ++fakePointerCounter


open class FakeLine(pointer: Long) : Line(
        pointer, LineSpec.Type.Other,
        timestamp = 0, rawPrefix = "", rawMessage = "",
        nick = null, isVisible = false, isHighlighted = false,
        LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low)


object MarkerLine : FakeLine(++fakePointerCounter)              // can have only one per buffer
class SquiggleLine : FakeLine(++fakePointerCounter)             // can have several of these


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
