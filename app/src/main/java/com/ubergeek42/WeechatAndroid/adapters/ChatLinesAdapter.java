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

package com.ubergeek42.WeechatAndroid.adapters;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.relay.Lines;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.WeechatAndroid.utils.LineView;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.ColorScheme;

import java.util.ArrayList;
import java.util.List;

import static com.ubergeek42.WeechatAndroid.R.layout.more_button;
import static com.ubergeek42.WeechatAndroid.R.layout.read_marker;
import static com.ubergeek42.WeechatAndroid.relay.Buffer.PRIVATE;
import static com.ubergeek42.WeechatAndroid.relay.Lines.HEADER_POINTER;
import static com.ubergeek42.WeechatAndroid.relay.Lines.MARKER_POINTER;

import static com.ubergeek42.WeechatAndroid.utils.Utils.Predicate;
import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;


public class ChatLinesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BufferEye {
    final private @Root Kitty kitty = Kitty.make("ChatLinesAdapter");

    private final AnimatedRecyclerView uiLines;
    private @Nullable Buffer buffer;
    private List<Line> lines = new ArrayList<>();
    volatile private List<Line> _lines = new ArrayList<>();

    @MainThread public ChatLinesAdapter(AnimatedRecyclerView animatedRecyclerView) {
        this.uiLines = animatedRecyclerView;
        setHasStableIds(true);
    }

    @MainThread public synchronized void setBuffer(@Nullable Buffer buffer) {
        this.buffer = buffer;
        kitty.setPrefix(buffer == null ? null : buffer.shortName);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// row
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends RecyclerView.ViewHolder {
        private LineView lineView;

        @MainThread Row(View view) {
            super(view);
            lineView = (LineView) view;
            lineView.setOnLongClickListener(CopyPaste.copyPaste);
        }

        @MainThread void update(Line line) {
            lineView.setTag(line);
            lineView.setText(line);
        }

        @MainThread void cancelAnimation() {
            lineView.cancelAnimation();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// read marker
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class ReadMarkerRow extends RecyclerView.ViewHolder {
        private View view;

        @MainThread ReadMarkerRow(View view) {
            super(view);
            this.view = view;
        }

        @MainThread void update() {
            view.setBackgroundColor(0xFF000000 | ColorScheme.get().chat_read_marker[0]);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// header
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Header extends RecyclerView.ViewHolder implements View.OnClickListener {
        private LineView title;
        private Button button;
        private ChatLinesAdapter adapter;
        private Lines.STATUS status = Lines.STATUS.CAN_FETCH_MORE;

        @MainThread Header(View header, ChatLinesAdapter adapter) {
            super(header);
            this.adapter = adapter;
            title = header.findViewById(R.id.title);
            title.setOnLongClickListener(CopyPaste.copyPaste);
            button = header.findViewById(R.id.button_more);
            button.setOnClickListener(this);
        }

        @MainThread void update() {
            if (adapter.buffer == null) return;
            updateButton();
            updateTitle();
        }

        @MainThread private void updateButton() {
            if (adapter.buffer == null) return;
            final Lines.STATUS s = adapter.buffer.getLinesStatus();
            if (status == s) return;
            status = s;
            if (s == Lines.STATUS.EVERYTHING_FETCHED) {
                button.setVisibility(View.GONE);
            } else {
                button.setVisibility(View.VISIBLE);
                boolean more = s == Lines.STATUS.CAN_FETCH_MORE;
                button.setEnabled(more);
                button.setText(button.getContext().getString(more ? R.string.more_button : R.string.more_button_fetching));
            }
        }

        @MainThread private void updateTitle() {
            if (adapter.buffer == null) return;
            Line titleLine = adapter.buffer.titleLine;
            if (titleLine == null || TextUtils.isEmpty(titleLine.spannable) || !adapter.buffer.linesAreReady()) {
                title.setVisibility(View.GONE);
                return;
            }
            title.setVisibility(View.VISIBLE);
            Utils.setBottomMargin(title, button.getVisibility() == View.GONE ? (int) P._4dp : 0);
            title.setText(titleLine);
            title.setTag(titleLine);
        }

        @MainThread @Override public void onClick(View v) {
            if (adapter.buffer == null) return;
            adapter.buffer.requestMoreLines();
            updateButton();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// RecyclerView.Adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static int HEADER_TYPE = -1, LINE_TYPE = 0, MARKER_TYPE = 1;

    @MainThread @Override public int getItemViewType(int position) {
        long pointer = lines.get(position).pointer;
        if (pointer == HEADER_POINTER) return HEADER_TYPE;
        if (pointer == MARKER_POINTER) return MARKER_TYPE;
        return LINE_TYPE;
    }

    @MainThread @Override public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater i = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER_TYPE) return new Header(i.inflate(more_button, parent, false), this);
        else if (viewType == MARKER_TYPE) return new ReadMarkerRow(i.inflate(read_marker, parent, false));
        else return new Row(new LineView(parent.getContext()));
    }

    @MainThread @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        long pointer = lines.get(position).pointer;
        if (pointer == HEADER_POINTER) ((Header) holder).update();
        else if (pointer == MARKER_POINTER) ((ReadMarkerRow) holder).update();
        else ((Row) holder).update(lines.get(position));
    }

    @MainThread @Override public int getItemCount() {
        return lines.size();
    }

    @MainThread @Override public long getItemId(int position) {
        return lines.get(position).pointer;
    }

    @MainThread @Override public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof Row) ((Row) holder).cancelAnimation();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get new lines, perform a simple diff and dispatch change notifications to RecyclerView
    // this might be called by multiple threads in rapid succession
    // in case non-main thread calls this before the Runnable that sets `lines` is executed,
    // store the new list in `_lines` so that we can produce a proper diff
    @AnyThread private synchronized void onLinesChanged() {
        if (buffer == null) return;
        final ArrayList<Line> newLines = buffer.getLinesCopy();

        final boolean hack = _lines.size() == 1 && newLines.size() > 1;

        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(_lines, newLines), false);
        _lines = newLines;

        Weechat.runOnMainThreadASAP(() -> {
            lines = newLines;
            diffResult.dispatchUpdatesTo(ChatLinesAdapter.this);
            if (uiLines.getOnBottom()) {
                if (hack) uiLines.scrollToPosition(getItemCount() - 1);
                else uiLines.smoothScrollToPosition(getItemCount() - 1);
            }
            else uiLines.flashScrollbar();
            uiLines.scheduleAnimationRestoring();
        });
    }

    @AnyThread private void updateHeader() {
        Weechat.runOnMainThread(() -> notifyItemChanged(0));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // increasing `style` will make all ViewHolders update visual characteristics
    @MainThread @Override public synchronized void onGlobalPreferencesChanged(boolean numberChanged) {
        if (numberChanged && buffer != null) {
            onLinesChanged();
        } else {
            notifyItemRangeChanged(0, _lines.size());
        }
    }

    @WorkerThread @Override public void onLinesListed() {
        onLinesChanged();
        updateHeader();
    }

    @AnyThread @Override public void onLineAdded() {    // todo change to @WorkerThread
        onLinesChanged();
    }

    @WorkerThread @Override public void onPropertiesChanged() {
        updateHeader();
    }

    @WorkerThread @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread public void loadLinesWithoutAnimation() {
        if (buffer == null) return;
        uiLines.disableAnimationForNextUpdate();
        onLinesChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// find hot line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static int HOT_LINE_LOST = -1;
    private final static int HOT_LINE_NOT_PRESENT = -3;

    @MainThread @Cat("Scrolling") public void scrollToHotLineIfNeeded() {
        final int idx = findHotLine();
        if (idx == HOT_LINE_NOT_PRESENT) return;
        if (idx == HOT_LINE_LOST) Weechat.showShortToast(R.string.autoscroll_no_line);
        // run scrolling slightly delayed so that stuff on current thread doesn't get in the way
        else Weechat.runOnMainThread(() -> uiLines.smoothScrollToPositionAfterAnimation(idx), 100);
    }

    @MainThread @Cat(value="Scrolling", exit=true) private int findHotLine() {
        assertThat(buffer).isNotNull();
        assertThat(buffer.linesAreReady()).isTrue();
        final List<Line> lines = _lines;

        int skip = buffer.getHotCount();
        if (skip == 0) return HOT_LINE_NOT_PRESENT;

        Predicate<Line> p = (buffer.type == PRIVATE) ? (l) -> l.type == Line.LINE_MESSAGE :
                (l) -> l.highlighted;

        for (int idx = lines.size() - 1; idx >= 0; idx--)
            if (p.test(lines.get(idx)) && --skip == 0) return idx;

        return HOT_LINE_LOST;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class DiffCallback extends DiffUtil.Callback {
        private List<Line> oldLines, newLines;

        DiffCallback(List<Line> oldLines, List<Line> newLines) {
            this.oldLines = oldLines;
            this.newLines = newLines;
        }

        @Override public int getOldListSize() {
            return oldLines.size();
        }

        @Override public int getNewListSize() {
            return newLines.size();
        }

        @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldLines.get(oldItemPosition).pointer == newLines.get(newItemPosition).pointer;
        }

        @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }
    }
}