// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateUtils
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Linkify.linkify
import com.ubergeek42.WeechatAndroid.utils.SHOULD_EMOJIFY
import com.ubergeek42.WeechatAndroid.utils.emojify
import com.ubergeek42.WeechatAndroid.utils.removeConsecutiveElementsLeavingFirst
import com.ubergeek42.WeechatAndroid.utils.replaceFirstWith
import com.ubergeek42.WeechatAndroid.utils.synchronizedInvalidatableLazy
import lbmq.LinkedBlockingMultiQueue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.concurrent.thread
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

    // Set to true when adding squiggle lines;
    // set to false when replacing lines with those from the server.
    // As lines may disappear due to new lines,
    // true value does not necessarily guarantee that lines have squiggles.
    private var mayHaveSquiggleLines = false

    /** Display marker when day changes */
    var displayDayChange = false
    private val dayChangeInserter = DayChangeInserter()

    // After reconnecting, we start receiving new lines.
    // However, we can't just add these lines to the existing lines,
    // as WeeChat may have received more lines while we were not connected to it.
    //
    // To work around the issue, we add a squiggly line between the old lines and the new ones,
    // unless we can tell if the last line on the server is the same as we have.
    // Note that the last *visible* pointer in WeeChat is determined by looking at
    // a limited amount of last lines, and thus might be absent.
    //
    // If there are several calls to this method in a row,
    // there can be consecutive squiggles in the unfiltered and filtered lines.
    // It is possible to not add one squiggle line next to another,
    // however it would complicate code somewhat,
    // and we would have to ensure that the same logic applies in `replaceLines`.
    // For simplicity, remove consecutive squiggle lines in `getCopy`.
    //
    // Note that the visibility of lines may change.
    // While it may seem that this can affect visibility of the nearby squiggle lines,
    // at the point when this method is called we can (almost) reliably tell
    // if the *visible* lines are continuous or not,
    // so I think that the visibility of the squiggle line will remain correct.
    //
    // Also note that we do not consider here the scenario
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
            mayHaveSquiggleLines = true
        }
    }

    // it might look like there's a room for optimization here,
    // but these uses very optimized array operations underneath and
    // getting this work faster is probably just not feasible or worth the effort
    fun getCopy(): ArrayList<Line> {
        val lines = (if (P.filterLines) filtered else unfiltered).let {
            if (displayDayChange) dayChangeInserter.insert(it) else it
        }
        val skip = if (P.filterLines) skipFiltered else skipUnfiltered

        return ArrayList<Line>(lines.size + 2).apply {
            add(headerLine)
            addAll(lines)
            if (skip in 0..lines.size) add(readMarkerIndex(skip), MarkerLine)

            if (mayHaveSquiggleLines) {
                while (isNotEmpty() && first() is SquiggleLine) removeAt(0) // removeFirst
                while (isNotEmpty() && last() is SquiggleLine) removeAt(lastIndex) // removeLast
                removeConsecutiveElementsLeavingFirst { it is SquiggleLine }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines

    fun replaceLines(lines: Collection<Line>) {
        if (status != Status.Fetching) return
        unfiltered.clear()
        filtered.clear()

        for (line in lines) {
            addLast(line)
        }
        mayHaveSquiggleLines = false
    }

    fun replaceLine(line: Line) {
        unfiltered.replaceFirstWith(line, predicate = { it.pointer == line.pointer })
        filtered.clear()
        unfiltered.forEach { line -> if (line.isVisible) filtered.add(line) }
        dayChangeInserter.clear()
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
        dayChangeInserter.clear()

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

    val namesThatSpokeLast: Iterator<String> get() = iterator {
        filtered.asReversed().forEach { line ->
            if (line.type === LineSpec.Type.IncomingMessage) {
                line.nick?.let { yield(it) }
            }
        }
    }

    fun clearPrecomputedLayoutsEtc() {
        unfiltered.forEach { it.clearPrecomputerLayoutEtc() }
    }

    // process lines that are gre going to be displayed, backwards, on a background thread pool.
    // this method gets called after line filter change, so it does get to process all needed lines
    fun ensurePrecomputedLayoutsInBackground() {
        val target = if (P.filterLines) filtered else unfiltered
        BackgroundLinePrecomputedLayoutProcessor.process(target.reversed())
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

    private var headerLineDelegate = synchronizedInvalidatableLazy { HeaderLine.make(title, status) }
    private val headerLine by headerLineDelegate
}


private const val MAX_C_POINTER_VALUE = 0x200000000000000       // 2⁵⁷
private var fakePointerCounter = MAX_C_POINTER_VALUE
val TITLE_LINE_POINTER = ++fakePointerCounter


private fun List<Line>.readMarkerIndex(skip: Int): Int {
    var index = size
    repeat(skip) {
        index--
        if (this[index] is DateChangeMarker) index--
    }
    if (index > 0 && this[index - 1] is DateChangeMarker) index--
    return index
}


private class DayChangeInserter {
    private var outputLines: List<Line>? = null
    private var inputLines: List<Line>? = null
    private var zoneId: ZoneId? = null

    /**
     * Clear cached lines
     *
     * This must be called whenever the underlying lines change.
     *
     */
    fun clear() {
        outputLines = null
    }

    /**
     * Insert day change markers into the given `lines`
     *
     * Result is cached for the given `lines` until [clear] is called or the timezone changes.
     *
     * @param lines lines to process
     * @return lines with day change markers inserted
     */
    fun insert(lines: List<Line>): List<Line> {
        val zone = ZoneId.systemDefault()
        val outputLines = outputLines
        if (outputLines != null && lines === this.inputLines && zone == this.zoneId) return outputLines

        this.inputLines = lines
        this.zoneId = zone
        return insertDayChangeMarkers(lines, zone).also { this@DayChangeInserter.outputLines = it }
    }

    private fun insertDayChangeMarkers(lines: List<Line>, zone: ZoneId): List<Line> {
        val output = ArrayList<Line>(lines.size + estimatedDayChanges(lines))
        var oldDate: LocalDate? = null
        var currentTimestamp = Long.MAX_VALUE
        var nextTimestamp = Long.MIN_VALUE

        for (line in lines) {
            if (line !is SquiggleLine && line.timestamp !in currentTimestamp..<nextTimestamp) {
                val newDate = Instant.ofEpochMilli(line.timestamp).atZone(zone).toLocalDate()
                currentTimestamp = newDate.atStartOfDay(zone).toInstant().toEpochMilli()
                nextTimestamp = newDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                output.add(makeDateChangeMarker(oldDate, newDate))
                oldDate = newDate
            }
            output.add(line)
        }
        return output
    }

    private fun estimatedDayChanges(lines: List<Line>): Int {
        val first = lines.firstOrNull { it !is SquiggleLine } ?: return 0
        val days = (System.currentTimeMillis() - first.timestamp) / DateUtils.DAY_IN_MILLIS + 1
        return days.coerceIn(0, lines.size.toLong()).toInt()
    }
}


private fun makeDateChangeMarker(oldDate: LocalDate?, newDate: LocalDate): Line {
    val tstamp = newDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
    var msg = newDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    if (oldDate != null && oldDate.plusDays(1) != newDate) {
        msg += " (" +
                oldDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
                ")"
    }
    msg += " --"

    return DateChangeMarker(dateChangePointer(oldDate, newDate), tstamp, msg)
}


private fun dateChangePointer(oldDate: LocalDate?, newDate: LocalDate): Long {
    // bits: new year 32 and up, new day 21–29, old year 9–20, old day 0–8
    var datePointer = (MAX_C_POINTER_VALUE shl 2) + (newDate.year.toLong() shl 32) + (newDate.dayOfYear shl 21)
    if (oldDate != null) {
        datePointer += (oldDate.year shl 9) + oldDate.dayOfYear
    }
    return datePointer
}


private class DateChangeMarker(pointer: Long, timestamp: Long, message: String) : FakeLine(
    pointer, true, timestamp, "--", message
)


open class FakeLine(
    pointer: Long, isVisible: Boolean = false, timestamp: Long = 0,
    rawPrefix: String = "", rawMessage: String = ""
) : Line(
    pointer, LineSpec.Type.Other, timestamp, rawPrefix, rawMessage,
    nick = null, isVisible = isVisible, isHighlighted = false,
    LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low
)


object MarkerLine : FakeLine(++fakePointerCounter)  // can have only one per buffer

class SquiggleLine(pointer: Long = ++fakePointerCounter, isVisible: Boolean = false) :
    FakeLine(pointer, isVisible)  // can have several of these


class HeaderLine(
    val titleSpanned: Spanned,
    val linesStatus: Lines.Status,
) : FakeLine(TITLE_LINE_POINTER) {
    override fun getIrcLikeString() = titleSpanned.toString()
    override fun getTimestampedIrcLikeString() = titleSpanned.toString()

    override fun visuallyEqualsTo(other: Line): Boolean {
        return titleSpanned == (other as? HeaderLine)?.titleSpanned && linesStatus == other.linesStatus
    }

    companion object {
        fun make(rawTitle: String, status: Lines.Status): HeaderLine {
            val titleSpanned = rawTitle.toSpannableWithWeechatColorsParsed(false, false)
                    .also {
                        if (SHOULD_EMOJIFY) emojify(it)
                        linkify(it)
                    }
            return HeaderLine(titleSpanned, status)
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


/**
 * Processes lines in thread pool with the threads having background priority,
 * which is supposed to help us not impact the responsiveness of the UI.
 *
 * When a collection of lines is submitted for processing,
 * it is added to a sub-queue of the queue that the threads take from.
 * This ensures that, even if several collections are added soon after each other,
 * they start being processed at once from start to end.
 *
 * By using a sub-queue pool of 3, we can be reasonably sure that
 * when switching to a far away buffer in the pager,
 * all three buffers that get attached to it get their bottom lines ready soon.
 * In my testing on a device, the precomputed layout are almost never processed on the main thread.
 */
object BackgroundLinePrecomputedLayoutProcessor {
    init {
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            thread(name = "lc-$i", isDaemon = true, priority = THREAD_PRIORITY_BACKGROUND) {
                while (true) {
                    queue.take().ensurePrecomputedLayout()
                }
            }
        }
    }

    private val subQueues: MutableList<LinkedBlockingMultiQueue<Int, Line>.SubQueue> = mutableListOf()

    private val queue = LinkedBlockingMultiQueue<Int, Line>()
            .apply {
                repeat(3) { key ->
                    addSubQueue(key, 0)
                    subQueues.add(getSubQueue(key))
                }
            }

    fun process(lines: Collection<Line>) {
        subQueues.minBy { it.size }.addAll(lines)
    }
}