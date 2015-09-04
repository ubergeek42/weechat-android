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

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferEye;
import com.ubergeek42.weechat.ColorScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter, BufferEye, AbsListView.OnScrollListener, AdapterView.OnItemLongClickListener {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = false;

    private FragmentActivity activity = null;

    private Buffer buffer;
    private Buffer.Line[] lines = new Buffer.Line[0];
    private LayoutInflater inflater;
    private ListView uiListView;
    private Typeface typeface = null;

    private boolean lastItemVisible = true;
    private boolean needMoveLastReadMarker = false;

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer, ListView uiListView) {
        if (DEBUG) logger.debug("ChatLinesAdapter({}, {})", activity, buffer);
        this.activity = activity;
        this.buffer = buffer;
        this.inflater = LayoutInflater.from(activity);
        this.uiListView = uiListView;
        uiListView.setOnScrollListener(this);
        uiListView.setOnItemLongClickListener(this);
    }
    public void setFont(String fontPath) {
        if (fontPath == null) {
            return;
        }
        typeface = Typeface.createFromFile(fontPath);
    }
    public void moveLastReadLine() {
        if (!needMoveLastReadMarker) {
            needMoveLastReadMarker = true;
            onLinesChanged();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getCount() {
        return lines.length;
    }

    @Override
    public Object getItem(int position) {
        return lines[position];
    }

    @Override
    public long getItemId(int position) {
        return lines[position].pointer;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View retview; // The view to return
        TextView textview;

        long lineID = getItemId(position);
        long lastLineRead = buffer.getLastViewedLine();
        // We only want to reuse textviews, not the special lastLineRead view
        if (lineID == lastLineRead || convertView instanceof RelativeLayout) {
            convertView = null; // Force re-creating this line
        }

        if (convertView == null) {
            if (lineID == lastLineRead) {
                retview = inflater.inflate(R.layout.chatview_line_last_read, null);
                textview = (TextView)retview.findViewById(R.id.chatline_message);
                retview.findViewById(R.id.separator).setBackgroundDrawable(new ColorDrawable(0xFF000000 | ColorScheme.currentScheme().getOptionColor("chat_read_marker")[0]));
            } else {
                textview = (TextView) inflater.inflate(R.layout.chatview_line, null);
                retview = textview;
            }
            textview.setTextColor(0xFF000000 | ColorScheme.currentScheme().getOptionColor("default")[0]);
            textview.setMovementMethod(LinkMovementMethod.getInstance());
        } else { // convertview is only ever not null for the simple case
            textview = (TextView) convertView;
            retview = textview;
        }

        textview.setTextSize(Buffer.Line.TEXT_SIZE);
        Buffer.Line line = (Buffer.Line) getItem(position);
        textview.setText(line.spannable);
        textview.setTag(line);
        if (typeface != null)
            textview.setTypeface(typeface);

        return retview;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private Spannable oldLastSpannable = null;

    public void readLinesFromBuffer() {
        oldLastSpannable = null;
        onLinesChanged();
    }

    @Override public void onLinesChanged() {
        if (DEBUG) logger.debug("onLinesChanged()");

        final int index, top;
        final Buffer.Line[] l;
        final Spannable lastSpannable;
        final boolean lineCountUnchanged, lastItemVisible, mustScrollOneLineUp;

        l = buffer.getLinesCopy();
        if (l.length == 0)
            return;

        lineCountUnchanged = lines.length == l.length;
        lastSpannable = l[l.length - 1].spannable;

        // return if there's nothing to update
        if (!needMoveLastReadMarker && lineCountUnchanged && lastSpannable == oldLastSpannable) return;
        oldLastSpannable = lastSpannable;

        if (needMoveLastReadMarker) {
            buffer.setLastViewedLine(l[l.length-1].pointer); // save this in the buffer object
            needMoveLastReadMarker = false;
        }

        // if last line is visible, scroll to bottom
        // this is required for earlier versions of android, apparently
        // if last line is not visible,
        // scroll one line up accordingly, so we stay in place
        // TODO: http://chris.banes.me/2013/02/21/listview-keeping-position/
        lastItemVisible = this.lastItemVisible;
        mustScrollOneLineUp = !lastItemVisible && lineCountUnchanged;
        if (mustScrollOneLineUp) {
            index = uiListView.getFirstVisiblePosition();
            View v = uiListView.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
        } else
            index = top = 0;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                // Update background color
                uiListView.setBackgroundDrawable(new ColorDrawable(0xFF000000 | ColorScheme.currentScheme().getOptionColor("default")[ColorScheme.OPT_BG]));

                lines = l;
                notifyDataSetChanged();
                if (lastItemVisible)
                    uiListView.setSelection(uiListView.getCount() - 1);
                else if (mustScrollOneLineUp)
                    uiListView.setSelectionFromTop(index - 1, top);
            }
        });
    }

    @Override public void onLinesListed() {}

    @Override public void onPropertiesChanged() {}

    @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// AbsListView.OnScrollListener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onScrollStateChanged(AbsListView absListView, int i) {}

    // this determines if the last item is visible
    // seriously, android?! is this this the only way to do that?!?! ffs
    @Override public void onScroll(AbsListView lw, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        lastItemVisible = (firstVisibleItem + visibleItemCount == totalItemCount);
    }

    @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Buffer.Line line = (Buffer.Line) parent.getItemAtPosition(position);
        line.clickDisabled = true;
        return false;
    }



}