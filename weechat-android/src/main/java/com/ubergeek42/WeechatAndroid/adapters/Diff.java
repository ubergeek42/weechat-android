/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.adapters;

import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import com.ubergeek42.WeechatAndroid.relay.Line;

import java.util.List;

class Diff {
    static Result calculateSimpleDiff(List<Line> oldLines, List<Line> newLines) {
        if (oldLines.isEmpty()) return completelyDifferentResult;

        // find the last old of old list in the new list
        int indexEndOld = oldLines.size() - 1;
        int indexEndNew = newLines.size() - 1;
        long lastPointer = oldLines.get(indexEndOld).pointer;
        for (; indexEndNew >= 0; indexEndNew--)
            if (newLines.get(indexEndNew).pointer == lastPointer) break;

        if (indexEndNew == -1) return completelyDifferentResult;

        // at this point we have at least one matching item
        int indexStartNew = indexEndNew, indexStartOld = indexEndOld;
        for (; indexStartNew >= 0 && indexStartOld >= 0; indexStartNew--, indexStartOld--)
            if (newLines.get(indexStartNew).pointer != oldLines.get(indexStartOld).pointer)
                // the match is only partial, so treat as full reset
                // this should only happen when user changed the filtering option
                return completelyDifferentResult;

        int bottomAdded = newLines.size() - 1 - indexEndNew;
        int topAdded = indexStartNew - indexStartOld;
        return new Result(false, topAdded, bottomAdded);
    }

    static class Result {
        final boolean completelyDifferent;
        final int topAdded;
        final int bottomAdded;

        Result(boolean completelyDifferent, int topAdded, int bottomAdded) {
            this.completelyDifferent = completelyDifferent;
            this.topAdded = topAdded;
            this.bottomAdded = bottomAdded;
        }

        @UiThread void dispatchDiff(RecyclerView.Adapter adapter, int headerItemCount) {
            if (completelyDifferent) {
                adapter.notifyDataSetChanged();
                return;
            }
            if (bottomAdded > 0) adapter.notifyItemRangeInserted(adapter.getItemCount() - headerItemCount + 1, bottomAdded);
            if (topAdded < 0) adapter.notifyItemRangeRemoved(headerItemCount, -topAdded);
            if (topAdded > 0) adapter.notifyItemRangeInserted(headerItemCount, topAdded);
        }

        boolean hasChanges() {
            return completelyDifferent || topAdded != 0 || bottomAdded != 0;
        }
    }

    final private static Result completelyDifferentResult = new Result(true, 0, 0);
}
