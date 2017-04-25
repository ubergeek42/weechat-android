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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.RecyclerView;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.weechat.ColorScheme;

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

    public @NonNull Line getLine(int position) {
        return lines.get(position);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// row
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends RecyclerView.ViewHolder implements View.OnLongClickListener {
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
            textView.setOnLongClickListener(this);
        }

        void update(Line line, int newStyle) {
            textView.setTag(line);
            textView.setText(line.spannable);
            if (style != (style = newStyle)) updateStyle();
        }

        void updateStyle() {
            textView.setTextSize(P.textSize);
            textView.setTypeface(P.typeface);
            textView.setTextColor(0xFF000000 | ColorScheme.get().defaul[0]);
        }

        @Override public boolean onLongClick(View v) {
            CopyPaste.onItemLongClick((TextView) v);
            return true;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// header

    private static class Header extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ViewGroup header;
        private Button button;
        private ChatLinesAdapter adapter;
        private Buffer.LINES status = Buffer.LINES.CAN_FETCH_MORE;

        Header(View header, ChatLinesAdapter adapter) {
            super(header);
            this.header = (ViewGroup) header;
            this.adapter = adapter;
            button = (Button) this.header.findViewById(R.id.button_more);
            button.setOnClickListener(this);
        }

        void update() {
            if (adapter.buffer == null) return;
            final Buffer.LINES s = adapter.buffer.getLineStatus();
            if (status == s) return;
            status = s;
            if (s == Buffer.LINES.EVERYTHING_FETCHED) {
                header.removeAllViews();
            } else {
                if (header.getChildCount() == 0) header.addView(button);
                boolean more = s == Buffer.LINES.CAN_FETCH_MORE;
                button.setEnabled(more);
                button.setTextColor(more ? 0xff80cbc4 : 0xff777777);
                button.setText(button.getContext().getString(more ? R.string.more_button : R.string.more_button_fetching));
            }
        }

        @Override public void onClick(View v) {
            if (adapter.buffer == null) return;
            adapter.buffer.requestMoreLines();
            update();
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
        if (position == 0) ((Header) holder).update();
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
    @UiThread @WorkerThread private synchronized void onLinesChanged(final boolean headerChanged) {
        if (buffer == null) return;
        final List<Line> newLines = Arrays.asList(buffer.getLinesCopy());
        boolean fullReset = false;
        boolean goToEnd = false;
        int indexEnd = -1, indexStart = -1, indexStartNew = -1;

        if (_lines.isEmpty()) {
            fullReset = true;
            if (_lines.isEmpty()) goToEnd = true;
        } else {
            long lastPointer = _lines.get(_lines.size()-1).pointer;
            for (int i = newLines.size() - 1; i >= 0; i--) {
                if (newLines.get(i).pointer == lastPointer) {
                    indexEnd = i;                                       // we found the end of old list in the new list
                    break;
                }
            }
            if (indexEnd == -1) {
                fullReset = goToEnd = true;                             // the new list is completely different to the old one
            } else {
                for (int n = indexEnd, o = _lines.size() - 1; n >= 0 && o >= 0; n--, o--) {
                    long newPointer = newLines.get(n).pointer;
                    if (newPointer == _lines.get(o).pointer) {
                        indexStart = o;                                 // we found the beginning of the match
                        indexStartNew = n;
                    } else {
                        break;
                    }
                }
                if  (indexStartNew != 0 && indexStart != 0) {
                    fullReset = true;                                   // the match is only partial
                }
            }
        }

        final boolean _fullReset = fullReset, _goToEnd = goToEnd;
        final int add = newLines.size() - 1 - indexEnd;                 // number of lines added on the end
        final int rem = (fullReset) ? 0 : indexStart - indexStartNew;   // number of lines deleted in the beginning; negative means some lines were added

        _lines = newLines;

        activity.runOnUiThread(new Runnable() {
            @SuppressWarnings("PointlessArithmeticExpression")
            @Override public void run() {
                lines = newLines;
                if (_fullReset) {
                    notifyDataSetChanged();
                    if (_goToEnd) uiLines.scrollToPosition(lines.size() - 1 + 1);
                } else {
                    if (add > 0) {
                        notifyItemRangeInserted(lines.size() + 1, add);
                        if (uiLines.getOnBottom()) uiLines.smoothScrollToPosition(lines.size() - 1 + 1);
                        else uiLines.flashScrollbar();
                    }
                    if (rem > 0) notifyItemRangeRemoved(0 + 1, rem);
                    if (rem < 0) notifyItemRangeInserted(0 + 1, -rem);
                }
                if (headerChanged) notifyItemChanged(0);
                uiLines.scheduleAnimationRestoring();
            }
        });
    }

    private long readMarkerLine = -1;

    @UiThread synchronized public void moveReadMarker() {
        if (buffer == null || buffer.readMarkerLine == readMarkerLine) return;
        uiLines.disableAnimationForNextUpdate();
        for (int i = 0; i < _lines.size(); i++) {
            Line line = _lines.get(i);
            if (line.pointer == readMarkerLine || line.pointer == buffer.readMarkerLine)
                notifyItemChanged(i + 1);
        }
        uiLines.scheduleAnimationRestoring();
        readMarkerLine = buffer.readMarkerLine;
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
        onLinesChanged(true);
    }

    @Override public void onLineAdded(final Line line, final boolean removed) {
        onLinesChanged(false);
    }

    @Override public void onPropertiesChanged() {}
    @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread public void loadLinesWithoutAnimation() {
        uiLines.disableAnimationForNextUpdate();
        onLinesChanged(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// find hot line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public final static int HOT_LINE_LOST = -1;
    public final static int HOT_LINE_NOT_READY = -2;
    public final static int HOT_LINE_NOT_PRESENT = -3;

    @UiThread public int findHotLine() {
        if (buffer == null || !buffer.isWatched || !buffer.holdsAllLines) return HOT_LINE_NOT_READY;
        int highlights = buffer.highlights;
        int privates = (buffer.type == Buffer.PRIVATE) ? buffer.unreads : 0;
        if ((highlights | privates) == 0) return HOT_LINE_NOT_PRESENT;

        int count = lines.size(), idx = -1;

        if (privates > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = lines.get(idx);
                if (line.type == Line.LINE_MESSAGE && --privates == 0) break;
            }
        } else if (highlights > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = lines.get(idx);
                if (line.highlighted && --highlights == 0) break;
            }
        }
        return (idx < 0) ? HOT_LINE_LOST : idx + 1;
    }
}