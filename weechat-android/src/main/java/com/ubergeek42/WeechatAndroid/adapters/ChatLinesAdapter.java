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
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter, BufferEye, AbsListView.OnScrollListener, AdapterView.OnItemLongClickListener {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = false;

    private FragmentActivity activity = null;

    private Buffer buffer;
    private Buffer.Line[] lines = new Buffer.Line[0];
    private LayoutInflater inflater;
    private ListView ui_listview;
    private Typeface typeface = null;

    private boolean last_item_visible = true;
    private boolean need_move_last_read_marker = false;

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer, ListView ui_listview) {
        if (DEBUG) logger.debug("ChatLinesAdapter({}, {})", activity, buffer);
        this.activity = activity;
        this.buffer = buffer;
        this.inflater = LayoutInflater.from(activity);
        this.ui_listview = ui_listview;
        ui_listview.setOnScrollListener(this);
        ui_listview.setOnItemLongClickListener(this);
    }
    public void setFont(String font_path) {
        if (font_path == null) {
            return;
        }
        typeface = Typeface.createFromFile(font_path);
    }
    public void moveLastReadLine() {
        if (!need_move_last_read_marker) {
            need_move_last_read_marker = true;
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
        long last_line_read = buffer.getLastViewedLine();
        // We only want to reuse textviews, not the special last_line_read view
        if (lineID == last_line_read || convertView instanceof RelativeLayout) {
            convertView = null; // Force re-creating this line
        }

        if (convertView == null) {
            if (lineID == last_line_read) {
                retview = inflater.inflate(R.layout.chatview_line_last_read, null);
                textview = (TextView)retview.findViewById(R.id.chatline_message);
            } else {
                textview = (TextView) inflater.inflate(R.layout.chatview_line, null);
                retview = textview;
            }
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

    private Spannable old_last_spannable = null;

    public void readLinesFromBuffer() {
        old_last_spannable = null;
        onLinesChanged();
    }

    @Override public void onLinesChanged() {
        if (DEBUG) logger.debug("onLinesChanged()");

        final int index, top;
        final Buffer.Line[] l;
        final Spannable last_spannable;
        final boolean line_count_unchanged, last_item_visible, must_scroll_one_line_up;

        l = buffer.getLinesCopy();
        if (l.length == 0)
            return;

        line_count_unchanged = lines.length == l.length;
        last_spannable = l[l.length - 1].spannable;

        // return if there's nothing to update
        if (!need_move_last_read_marker && line_count_unchanged && last_spannable == old_last_spannable) return;
        old_last_spannable = last_spannable;

        if (need_move_last_read_marker) {
            buffer.setLastViewedLine(l[l.length-1].pointer); // save this in the buffer object
            need_move_last_read_marker = false;
        }

        // if last line is visible, scroll to bottom
        // this is required for earlier versions of android, apparently
        // if last line is not visible,
        // scroll one line up accordingly, so we stay in place
        // TODO: http://chris.banes.me/2013/02/21/listview-keeping-position/
        last_item_visible = this.last_item_visible;
        must_scroll_one_line_up = !last_item_visible && line_count_unchanged;
        if (must_scroll_one_line_up) {
            index = ui_listview.getFirstVisiblePosition();
            View v = ui_listview.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
        } else
            index = top = 0;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                lines = l;
                notifyDataSetChanged();
                if (last_item_visible)
                    ui_listview.setSelection(ui_listview.getCount() - 1);
                else if (must_scroll_one_line_up)
                    ui_listview.setSelectionFromTop(index - 1, top);
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
        last_item_visible = (firstVisibleItem + visibleItemCount == totalItemCount);
    }

    @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Buffer.Line line = (Buffer.Line) parent.getItemAtPosition(position);
        line.disableClick();
        return false;
    }



}