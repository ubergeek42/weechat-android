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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.weechat.ColorScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter, BufferEye, AbsListView.OnScrollListener {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = false;

    private WeechatActivity activity = null;

    private Buffer buffer;
    private Line[] lines = new Line[0];
    private LayoutInflater inflater;
    private ListView uiListView;
    private @Nullable Typeface typeface = null;

    private boolean lastItemVisible = true;
    public boolean needMoveLastReadMarker = false;

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer, ListView uiListView) {
        if (DEBUG) logger.debug("ChatLinesAdapter({}, {})", activity, buffer);
        this.activity = (WeechatActivity) activity;
        this.buffer = buffer;
        this.inflater = LayoutInflater.from(activity);
        this.uiListView = uiListView;
        uiListView.setOnScrollListener(this);
    }
    public void setFont(@NonNull String fontPath) {
        typeface = ("".equals(fontPath)) ? null : Typeface.createFromFile(fontPath);
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

        boolean mustDrawReadMarker = getItemId(position) == buffer.readMarkerLine;

        // we only want to reuse TextViews, not the special lastLineRead view,
        // so force view recreation
        if (mustDrawReadMarker || convertView instanceof RelativeLayout)
            convertView = null;

        if (convertView == null) {
            if (mustDrawReadMarker) {
                retview = inflater.inflate(R.layout.chatview_line_read_marker, null);
                textview = (TextView)retview.findViewById(R.id.chatline_message);
                //noinspection deprecation
                retview.findViewById(R.id.separator).setBackgroundDrawable(
                        new ColorDrawable(0xFF000000 | ColorScheme.get().chat_read_marker[0]));
            } else {
                textview = (TextView) inflater.inflate(R.layout.chatview_line, null);
                retview = textview;
            }
            textview.setTextColor(0xFF000000 | ColorScheme.get().defaul[0]);
            textview.setMovementMethod(LinkMovementMethod.getInstance());
        } else { // convertview is only ever not null for the simple case
            textview = (TextView) convertView;
            retview = textview;
        }

        textview.setTextSize(Line.TEXT_SIZE);
        Line line = (Line) getItem(position);
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
        final Line[] l;
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

        // if last line is visible, scroll to bottom
        // this is required for earlier versions of android, apparently
        // if last line is not visible,
        // scroll one line up accordingly, so we stay in place
        // TODO: http://chris.banes.me/2013/02/21/listview-keeping-position/
        lastItemVisible = this.lastItemVisible;
        mustScrollOneLineUp = !lastItemVisible && lineCountUnchanged && !needMoveLastReadMarker;    //TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! last piece here
        if (mustScrollOneLineUp) {
            logger.info("ONE LINE UP");
            index = uiListView.getFirstVisiblePosition();
            View v = uiListView.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
        } else
            index = top = 0;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                lines = l;
                notifyDataSetChanged();
                if (lastItemVisible)
                    uiListView.setSelection(uiListView.getCount() - 1);
                else if (mustScrollOneLineUp)
                    uiListView.setSelectionFromTop(index - 1, top);
            }
        });

        needMoveLastReadMarker = false;
    }

    @Override public void onLinesListed() {}

    @Override public void onPropertiesChanged() {}

    @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// AbsListView.OnScrollListener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    boolean userIsScrolling = false;
    private int prevBottomHidden = 0;

    @Override public void onScrollStateChanged(AbsListView absListView, int i) {
        userIsScrolling = (i != SCROLL_STATE_IDLE);
    }

    // this determines how many items are not visible in the ListView
    // the difference, if any, and if user is actually scrolling, is sent to toolbar controller
    //   (this is needed to prevent inadvertent toolbar showing/hiding when changing lines)
    // also, determine if we are on our last line
    @Override public void onScroll(AbsListView lw, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        final int bottomHidden = totalItemCount - firstVisibleItem - visibleItemCount;
        lastItemVisible = bottomHidden == 0;
        if (userIsScrolling)
            activity.toolbarController.onUserScroll(bottomHidden, prevBottomHidden);
        prevBottomHidden = bottomHidden;
    }
}