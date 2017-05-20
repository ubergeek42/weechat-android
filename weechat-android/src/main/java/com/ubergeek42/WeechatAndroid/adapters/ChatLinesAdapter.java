/*******************************************************************************
 * Copyright 2012 Keith Johnson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.adapters;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.ColorScheme;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ubergeek42.WeechatAndroid.R.layout.more_button;

public class ChatLinesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BufferEye {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = true;

    private WeechatActivity activity = null;
    private AnimatedRecyclerView uiLines;
    private @Nullable Buffer buffer;
    private List<Line> lines = new ArrayList<>();
    private List<Line> _lines = new ArrayList<>();

    private int style = 0;

    public ChatLinesAdapter(WeechatActivity activity, AnimatedRecyclerView animatedRecyclerView) {
        if (DEBUG) logger.debug("ChatLinesAdapter()");
        this.activity = activity;
        this.uiLines = animatedRecyclerView;
        setHasStableIds(true);
    }

    @UiThread public void setBuffer(@Nullable Buffer buffer) {
        this.buffer = buffer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// row
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends RecyclerView.ViewHolder {
        private View view;
        private TextView textView;
        private int style = -1;

        Row(View view) {
            super(view);
            this.view = view;
            if (view instanceof TextView) {
                textView = (TextView) view;
            } else {
                textView = (TextView) view.findViewById(R.id.chatline_message);
                this.view.findViewById(R.id.separator).setBackgroundColor(0xFF000000 | ColorScheme.get().chat_read_marker[0]);
            }
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setOnLongClickListener(CopyPaste.copyPaste);
        }

        void update(Line line, int newStyle) {
            textView.setTag(line);
            textView.setText(line.spannable);
            if (style != (style = newStyle)) updateStyle(textView);
        }
    }

    private static void updateStyle(TextView textView) {
        textView.setTextSize(P.textSize);
        textView.setTypeface(P.typeface);
        textView.setTextColor(0xFF000000 | ColorScheme.get().defaul[0]);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// header
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Header extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView title;
        private Button button;
        private ChatLinesAdapter adapter;
        private Buffer.LINES status = Buffer.LINES.CAN_FETCH_MORE;
        private Spannable topicText = null;
        private int style = -1;

        Header(View header, ChatLinesAdapter adapter) {
            super(header);
            this.adapter = adapter;
            title = (TextView) header.findViewById(R.id.title);
            title.setMovementMethod(LinkMovementMethod.getInstance());
            title.setOnLongClickListener(CopyPaste.copyPaste);
            button = (Button) header.findViewById(R.id.button_more);
            button.setOnClickListener(this);
        }

        void update(int newStyle) {
            if (adapter.buffer == null) return;
            updateButton();
            updateTitle(newStyle);
        }

        private void updateButton() {
            if (adapter.buffer == null) return;
            final Buffer.LINES s = adapter.buffer.getLineStatus();
            if (status == s) return;
            status = s;
            if (s == Buffer.LINES.EVERYTHING_FETCHED) {
                button.setVisibility(View.GONE);
            } else {
                button.setVisibility(View.VISIBLE);
                boolean more = s == Buffer.LINES.CAN_FETCH_MORE;
                button.setEnabled(more);
                button.setTextColor(more ? 0xff80cbc4 : 0xff777777);
                button.setText(button.getContext().getString(more ? R.string.more_button : R.string.more_button_fetching));
            }
        }

        private void updateTitle(int newStyle) {
            if (adapter.buffer == null) return;
            Spannable titleSpannable = adapter.buffer.titleSpannable;
            Line titleLine = adapter.buffer.titleLine;
            if (TextUtils.isEmpty(titleSpannable) || !adapter.buffer.holdsAllLines) {
                title.setVisibility(View.GONE);
                return;
            }
            title.setVisibility(View.VISIBLE);
            Utils.setBottomMargin(title, button.getVisibility() == View.GONE ? (int) P._4dp : 0);
            if (topicText != titleSpannable) {
                title.setText(topicText = titleSpannable);
                title.setTag(titleLine);
            }
            if (style != (style = newStyle)) updateStyle(title);
        }

        @Override public void onClick(View v) {
            if (adapter.buffer == null) return;
            adapter.buffer.requestMoreLines();
            updateButton();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// RecyclerView.Adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static int HEADER = -1, LINE = 0, LINE_MARKER = 1;
    final private static int HEADER_ID = -123;

    @Override public int getItemViewType(int position) {
        //logger.trace("getItemViewType({})", position);
        if (position == 0) return HEADER;
        return getItemId(position) == readMarkerLine ? LINE_MARKER : LINE;
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //logger.trace("onCreateViewHolder({})", viewType);
        if (viewType == HEADER) return new Header(LayoutInflater.from(parent.getContext()).inflate(more_button, parent, false), this);
        int res = (viewType == LINE) ? R.layout.chatview_line : R.layout.chatview_line_read_marker;
        return new Row(LayoutInflater.from(parent.getContext()).inflate(res, parent, false));
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        //logger.trace("onBindViewHolder(..., {}, {})", position);
        if (position == 0) ((Header) holder).update(style);
        else ((Row) holder).update(lines.get(position - 1), style);
    }

    @Override public int getItemCount() {
        //logger.trace("getItemCount()");
        return lines.size() + 1;
    }

    @Override public long getItemId(int position) {
        //logger.trace("getItemId({})", position);
        if (position == 0) return HEADER_ID;
        return lines.get(position - 1).pointer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get new lines, perform a simple diff and dispatch change notifications to RecyclerView
    // this might be called by multiple threads in rapid succession
    // in case non-main thread calls this before the Runnable that sets `lines` is executed,
    // store the new list in `_lines` so that we can produce a proper diff
    @UiThread @WorkerThread private synchronized void onLinesChanged() {
        if (buffer == null) return;

        final List<Line> newLines = Arrays.asList(buffer.getLinesCopy());
        readMarkerLine = buffer.visibleReadMarkerLine;
        final Diff.Result result = Diff.calculateSimpleDiff(_lines, newLines);
        if (!result.hasChanges()) return;
        _lines = newLines;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                lines = newLines;
                result.dispatchDiff(ChatLinesAdapter.this, 1);
                if (result.completelyDifferent) uiLines.scrollToPosition(getItemCount() - 1);
                if (result.bottomAdded > 0) {
                    if (uiLines.getOnBottom()) uiLines.smoothScrollToPosition(getItemCount() - 1);
                    else uiLines.flashScrollbar();
                }
                uiLines.scheduleAnimationRestoring();
            }
        });
    }

    private long readMarkerLine = -1;

    @UiThread synchronized public void moveReadMarkerToEnd() {
        if (buffer == null) return;
        if (!buffer.moveReadMarkerToEndAndTellIfChanged()) return;
        if (buffer.visibleReadMarkerLine == -1) return;
        uiLines.disableAnimationForNextUpdate();
        for (int i = 0; i < _lines.size(); i++) {
            Line line = _lines.get(i);
            if (line.pointer == readMarkerLine || line.pointer == buffer.visibleReadMarkerLine)
                notifyItemChanged(i + 1);
        }
        uiLines.scheduleAnimationRestoring();
        readMarkerLine = buffer.visibleReadMarkerLine;
    }

    private void updateHeader() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                notifyItemChanged(0);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // increasing `style` will make all ViewHolders update visual characteristics
    @Override @UiThread public void onGlobalPreferencesChanged() {
        style++;
        if (_lines.size() > 0) notifyItemRangeChanged(1, _lines.size() - 1 + 1);
    }

    @Override public void onLinesListed() {
        onLinesChanged();
        updateHeader();
    }

    @Override public void onLineAdded() {
        onLinesChanged();
    }

    @Override public void onPropertiesChanged() {
        updateHeader();
    }

    @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread public void loadLinesWithoutAnimation() {
        if (buffer == null) return;
        uiLines.disableAnimationForNextUpdate();
        readMarkerLine = buffer.readMarkerLine;
        onLinesChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// find hot line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread @WorkerThread public void scrollToHotLineIfNeeded() {
        final int idx = findHotLine();
        if (idx == HOT_LINE_NOT_READY || idx == HOT_LINE_NOT_PRESENT) return;
        highlights = privates = 0;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (idx == HOT_LINE_LOST) Toast.makeText(activity, activity.getString(R.string.autoscroll_no_line), Toast.LENGTH_SHORT).show();
                else uiLines.smoothScrollToPositionAfterAnimation(idx);
            }
        });
    }

    @UiThread public void storeHotLineInfo() {
        Assert.assertNotNull(buffer);
        highlights = buffer.highlights;
        privates = (buffer.type == Buffer.PRIVATE) ? buffer.unreads : 0;
    }

    private int highlights;
    private int privates;

    private final static int HOT_LINE_LOST = -1;
    private final static int HOT_LINE_NOT_READY = -2;
    private final static int HOT_LINE_NOT_PRESENT = -3;

    synchronized private int findHotLine() {
        Assert.assertNotNull(buffer);
        if (!buffer.isWatched || !buffer.holdsAllLines) return HOT_LINE_NOT_READY;
        if ((highlights | privates) == 0) return HOT_LINE_NOT_PRESENT;

        int count = _lines.size(), idx = -1;

        if (privates > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = _lines.get(idx);
                if (line.type == Line.LINE_MESSAGE && --privates == 0) break;
            }
        } else if (highlights > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = _lines.get(idx);
                if (line.highlighted && --highlights == 0) break;
            }
        }
        return (idx < 0) ? HOT_LINE_LOST : idx + 1;
    }
}