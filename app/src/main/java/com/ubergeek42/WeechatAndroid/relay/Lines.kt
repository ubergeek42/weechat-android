// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


// this class is supposed to be synchronized by Buffer
public class Lines {
    @SuppressWarnings("FieldCanBeLocal")
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

    private final static Line HEADER = new Line(HEADER_POINTER, LineSpec.Type.Other, 0, "", "", null, false, false, LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low);
    private final static Line MARKER = new Line(MARKER_POINTER, LineSpec.Type.Other, 0, "", "", null, false, false, LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low);

    private final ArrayDeque<Line> filtered = new ArrayDeque<>();
    private final ArrayDeque<Line> unfiltered = new ArrayDeque<>();

    private int skipUnfiltered = -1;
    private int skipFiltered = -1;

    private int skipUnfilteredOffset = -1;
    private int skipFilteredOffset = -1;

    private int maxUnfilteredSize;

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

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines

    // todo this assumes that size of new lines > size of old lines, and also
    // todo that maxUnfilteredSize <= size of new lines.
    // todo determine if violating these assumptions can lead to problems
    @WorkerThread void replaceLines(Collection<Line> lines) {
        if (status != STATUS.FETCHING) return;
        unfiltered.clear();
        filtered.clear();
        unfiltered.addAll(lines);
        for (Line line: lines) {
            if (line.isVisible) { filtered.add(line); }
        }
    }

    // note that rarely, especially when opening a buffer that weechat is loading backlog for at the
    // moment, we can get this call while status == STATUS.FETCHING. even though in this case we
    // will eventually receive the full lines again, we can't ignore these as the method addFirst()
    // doesn't take line position and lines are simply skipped if they are already present, which
    // would place these lines above lines already in the buffer. todo make addFirst take position?
    @WorkerThread void addLast(Line line) {
        int unfilteredSize = unfiltered.size();
        int maxUnfilteredSize = this.maxUnfilteredSize;
        boolean shouldRemoveFirstLine = unfilteredSize == maxUnfilteredSize;

        if (shouldRemoveFirstLine) {
            if (unfiltered.removeFirst().isVisible) filtered.removeFirst();
        }

        unfiltered.addLast(line);
        if (line.isVisible) filtered.addLast(line);

        if (status == STATUS.FETCHING) return;

        if (skipFiltered >= 0 && line.isVisible) skipFiltered++;
        if (skipUnfiltered >= 0) skipUnfiltered++;

        if (shouldRemoveFirstLine) {
            if (status != STATUS.CAN_FETCH_MORE) {
                if (!status.ready()) setSkipsUsingPointer();
                status = STATUS.CAN_FETCH_MORE;
            }
        } else if (unfilteredSize + 1 == maxUnfilteredSize) {
            if (status != STATUS.EVERYTHING_FETCHED) {
                if (!status.ready()) setSkipsUsingPointer();
                status = STATUS.EVERYTHING_FETCHED;
            }
        }
    }

    @AnyThread void clear() {
        filtered.clear();
        unfiltered.clear();
        maxUnfilteredSize = P.lineIncrement;
        status = STATUS.INIT;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread void onMoreLinesRequested(int newSize) {
        if (status != STATUS.INIT) maxUnfilteredSize = newSize;
        status = STATUS.FETCHING;
    }

    // in very rare cases status might not be FETCHING here, particularly when closing buffers
    // while the app is connecting and has already requested lines
    @WorkerThread void onLinesListed() {
        if (status != STATUS.FETCHING) return;
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

    @AnyThread void invalidateSpannables() {
        for (Line l: unfiltered) l.invalidateSpannable();
    }

    // process lines that are gre going to be displayed, backwards, on a background thread pool.
    // this method gets called after line filter change, so it does get to process all needed lines
    @AnyThread void ensureSpannables() {
        ArrayDeque<Line> target = P.filterLines ? filtered : unfiltered;
        Line[] snapshot = target.toArray(new Line[0]);
        Utils.runInBackground(() -> {
            for (int i = snapshot.length - 1; i >= 0; i--) snapshot[i].ensureSpannable();
        });
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
            if (line.isVisible) idx_f++;
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
}
