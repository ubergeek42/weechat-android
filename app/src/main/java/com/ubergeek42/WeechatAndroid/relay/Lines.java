// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;


// this class is supposed to be synchronized by Buffer
public class Lines {
    final private @Root Kitty kitty = Kitty.make();

    public final static int HEADER_POINTER = -123, MARKER_POINTER = -456;
    public enum STATUS {
        INIT,
        FETCHING,
        CAN_FETCH_MORE,
        EVERYTHING_FETCHED;

        // true if Lines contain all necessary line information and read marker stuff
        // note that the number of lines can be zero and read markers can be not preset
        boolean ready() {
            return this == CAN_FETCH_MORE || this == EVERYTHING_FETCHED;
        }
    }

    @NonNull STATUS status = STATUS.INIT;
    private long lastSeenLine = -1;

    private final static Line HEADER = new Line(HEADER_POINTER, null, null, null, false, false, new String[]{});
    private final static Line MARKER = new Line(MARKER_POINTER, null, null, null, false, false, new String[]{});

    private final ArrayDeque<Line> filtered = new ArrayDeque<>();
    private final ArrayDeque<Line> unfiltered = new ArrayDeque<>();

    private int skipUnfiltered = -1;
    private int skipFiltered = -1;

    private int skipUnfilteredOffset = -1;
    private int skipFilteredOffset = -1;

    private int maxUnfilteredSize = 0;

    @WorkerThread Lines(String name) {
        maxUnfilteredSize = P.lineIncrement;
        kitty.setPrefix(name);
    }

    @AnyThread @NonNull ArrayList<Line> getCopy() {
        int skip = P.filterLines ? skipFiltered : skipUnfiltered;
        ArrayList<Line> lines = new ArrayList<>(P.filterLines ? filtered : unfiltered);
        int marker = skip >= 0 && lines.size() > 0 ? lines.size() - skip : -1;
        if (marker > 0) lines.add(marker, MARKER);
        lines.add(0, HEADER);
        return lines;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread void addFirst(Line line) {
        assertEquals(status, STATUS.FETCHING);
        ensureSizeBeforeAddition();
        unfiltered.addFirst(line);
        if (line.visible) filtered.addFirst(line);
    }

    @WorkerThread void addLast(Line line) {
        if (status == STATUS.FETCHING) return;
        ensureSizeBeforeAddition();
        unfiltered.addLast(line);
        if (line.visible) filtered.addLast(line);

        if (skipFiltered >= 0 && line.visible) skipFiltered++;
        if (skipUnfiltered >= 0) skipUnfiltered++;

        // if we hit max size while the buffer is not open, behave as if lines were requested
        if (!status.ready() && unfiltered.size() == maxUnfilteredSize) onLinesListed();
    }

    @AnyThread void clear() {
        filtered.clear();
        unfiltered.clear();
        maxUnfilteredSize = P.lineIncrement;
        status = STATUS.INIT;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread void onMoreLinesRequested() {
        if (status != STATUS.INIT) maxUnfilteredSize += P.lineIncrement;
        status = STATUS.FETCHING;
    }

    @WorkerThread void onLinesListed() {
        status = unfiltered.size() == maxUnfilteredSize ? STATUS.CAN_FETCH_MORE : STATUS.EVERYTHING_FETCHED;
        setSkipsUsingPointer();
    }

    @MainThread int getMaxLines() {
        return maxUnfilteredSize;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread Iterator<Line> getDescendingFilteredIterator() {
        return filtered.descendingIterator();
    }

    @WorkerThread boolean contains(Line line) {
        for (Line l: unfiltered) if (line.pointer == l.pointer) return true;
        return false;
    }

    @AnyThread void processAllMessages(boolean force) {
        for (Line l: unfiltered) if (force) l.forceProcessMessage(); else l.processMessage();
    }

    @AnyThread void eraseProcessedMessages() {
        for (Line l: unfiltered) l.eraseProcessedMessage();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread void moveReadMarkerToEnd() {
        if (skipFilteredOffset >= 0 && skipUnfilteredOffset >= 0 && skipFiltered >= skipFilteredOffset && skipUnfiltered >= skipFilteredOffset) {
            skipFiltered -= skipFilteredOffset;
            skipUnfiltered -= skipUnfilteredOffset;
        } else {
            skipFiltered = skipUnfiltered = 0;
        }
        skipFilteredOffset = skipUnfilteredOffset = -1;
    }

    @MainThread void rememberCurrentSkipsOffset() {
        skipFilteredOffset = skipFiltered;
        skipUnfilteredOffset = skipUnfiltered;
        if (unfiltered.size() > 0) lastSeenLine = unfiltered.getLast().pointer;
    }

    @WorkerThread void setLastSeenLine(long lastSeenLine) {
        if (status.ready()) return;
        this.lastSeenLine = lastSeenLine;
        setSkipsUsingPointer();
    }

    @AnyThread long getLastSeenLine() {
        return lastSeenLine;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread private void setSkipsUsingPointer() {
        Iterator<Line> it = unfiltered.descendingIterator();
        int idx_f = 0, idx_u = 0;
        skipFiltered = skipUnfiltered = -1;
        while (it.hasNext()) {
            Line line = it.next();
            if (line.pointer == lastSeenLine) {
                skipFiltered = idx_f;
                skipUnfiltered = idx_u;
                return;
            }
            idx_u++;
            if (line.visible) idx_f++;
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread private void ensureSizeBeforeAddition() {
        if (unfiltered.size() == maxUnfilteredSize)
            if (unfiltered.removeFirst().visible) filtered.removeFirst();
    }
}
