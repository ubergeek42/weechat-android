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
package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;
import com.ubergeek42.WeechatAndroid.service.BufferListEye;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferListAdapter extends BaseAdapter implements BufferListEye {

    private static Logger logger = LoggerFactory.getLogger("BufferListAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    Activity activity;
    LayoutInflater inflater;
    BufferList buffer_list;
    private SharedPreferences prefs;
    private ArrayList<Buffer> buffers = new ArrayList<Buffer>();

    private static RelativeLayout.LayoutParams layout_params_closed;
    private static RelativeLayout.LayoutParams layout_params_open;

    static {
        layout_params_closed = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout_params_open = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout_params_closed.setMargins(0, 0, 0, 0);
        layout_params_open.setMargins(4, 0, 0, 0);
    }

    final static int[][] COLORS = new int[][] {
            {0xff0C131C, 0xff1D3A63},
            {0xff20140E, 0xff734222},
            {0xff0D1C0C, 0xff1D631D},
            {0xff1D0C17, 0xff671E48},
            {0xff201F0E, 0xff737322},
            {0xff0E0C1C, 0xff291D63},
            {0xff20180E, 0xff735322},
            {0xff0C191C, 0xff1D5163},
            {0xff1F0D0E, 0xff732222},
            {0xff1A1E0D, 0xff586B1F},
            {0xff160C1C, 0xff4C1D63},
            {0xff201C0E, 0xff736322}
    };

    final static int[][] COLORS2 = new int[][] {
            {0xff525252, 0xff6c6c6c}, // other
            {0xff44525f, 0xff596c7d}, // channel
            {0xff57474f, 0xff735e69}, // private
    };
    
    public BufferListAdapter(Activity activity, BufferList buffer_list) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.buffer_list = buffer_list;
    }

    @Override
    public int getCount() {
        return buffers.size();
    }

    @Override
    public Buffer getItem(int position) {
        return buffers.get(position);
    }

    @Override
    public void onBuffersChanged() {
        final ArrayList<Buffer> buffers = buffer_list.getBufferListCopy();
        if (BufferList.SORT_BUFFERS) Collections.sort(buffers, bufferComparator);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BufferListAdapter.this.buffers = buffers;
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onBuffersSlightlyChanged() {
        if (BufferList.SORT_BUFFERS) Collections.sort(buffers, bufferComparator);            // TODO: is this thread safe???
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public long getItemId(int position) {return position;}

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.bufferlist_item, parent, false);
            holder = new ViewHolder();
            holder.ui_buffer = (TextView) convertView.findViewById(R.id.buffer);
            holder.ui_warm = (TextView) convertView.findViewById(R.id.buffer_warm);
            holder.ui_hot = (TextView) convertView.findViewById(R.id.buffer_hot);
            convertView.setTag(holder);
        } else
            holder = (ViewHolder) convertView.getTag();

        Buffer buffer = getItem(position);

        holder.ui_buffer.setText((BufferList.SHOW_TITLE && buffer.printable2 != null) ? buffer.printable2 : buffer.printable1);

        int unreads = buffer.unreads;
        int highlights = buffer.highlights;

        int important = (highlights > 0 || (unreads > 0 && buffer.type == Buffer.PRIVATE)) ? 1 : 0;

        //holder.ui_buffer.setBackgroundColor(COLORS[buffer.number % COLORS.length][important]);
        holder.ui_buffer.setBackgroundColor(COLORS2[buffer.type][important]);
        holder.ui_buffer.setLayoutParams(buffer.is_open ? layout_params_open : layout_params_closed);

        if (highlights > 0) {
            holder.ui_hot.setText(Integer.toString(highlights));
            holder.ui_hot.setVisibility(View.VISIBLE);
        } else
            holder.ui_hot.setVisibility(View.INVISIBLE);

        if (unreads > 0) {
            holder.ui_warm.setText(Integer.toString(unreads));
            holder.ui_warm.setVisibility(View.VISIBLE);
        } else
            holder.ui_warm.setVisibility(View.GONE);

        return convertView;
    }

    private static class ViewHolder {
        TextView ui_hot;
        TextView ui_warm;
        TextView ui_buffer;
    }

    private final Comparator<Buffer> bufferComparator = new Comparator<Buffer>() {
        @Override
        public int compare(Buffer b1, Buffer b2) {
            int h1 = b1.highlights;
            int h2 = b2.highlights;
            if (h1 == h2) return b2.unreads - b1.unreads;
            else return b2.highlights - b1.highlights;
        }
    };
}
