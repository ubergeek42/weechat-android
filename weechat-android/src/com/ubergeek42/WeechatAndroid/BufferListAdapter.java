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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;

public class BufferListAdapter extends BaseAdapter implements OnSharedPreferenceChangeListener {
    Activity parentActivity;
    LayoutInflater inflater;
    private SharedPreferences prefs;
    private ArrayList<Buffer> allBuffers = new ArrayList<Buffer>();
    private ArrayList<Buffer> filteredBuffers = new ArrayList<Buffer>();
    private String currentFilter = null;
    
    
    private boolean hideBufferTitles = false;
    private boolean sortedBuffers = false;
    
    public BufferListAdapter(Activity parentActivity) {
        this.parentActivity = parentActivity;
        this.inflater = LayoutInflater.from(parentActivity);

        prefs = PreferenceManager.getDefaultSharedPreferences(parentActivity.getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        hideBufferTitles = prefs.getBoolean("hide_buffer_titles", false);
    }

    @Override
    public int getCount() {
        return filteredBuffers.size();
    }

    @Override
    public Buffer getItem(int position) {
        return filteredBuffers.get(position);
    }
    
    /**
     * Filter the buffer list to only contain things that match the string
     * @param filter - the string to filter on
     */
    public void filterBuffers(String filter) {
        filteredBuffers.clear();
        
        // Handle the case of a missing/empty filter
        if (filter == null || filter.length()==0) {
            filteredBuffers.addAll(allBuffers);
        } else {
            // Filter based on what they typed
            currentFilter = filter.toLowerCase();
            for(Buffer b: allBuffers) {
                if (b.getFullName().toLowerCase().contains(currentFilter)) {
                    filteredBuffers.add(b);
                }
            }
        }
        if (sortedBuffers) Collections.sort(filteredBuffers, bufferComparator);
        notifyDataSetChanged();
    }

    public void setBuffers(ArrayList<Buffer> buffers) {
        this.allBuffers = buffers;
        filterBuffers(currentFilter);
    }

    public void enableSorting(boolean enableSorting) {
        sortedBuffers = enableSorting;
    }

    /**
     * Find the position of a buffer(by name) in the list
     * 
     * @param b
     *            - The buffer to find
     * @return Position of the buffer if found -1 if not found
     */
    public int findBufferPosition(Buffer b) {
        for (int i = 0; i < filteredBuffers.size(); i++) {
            if (b.getFullName().equals(filteredBuffers.get(i).getFullName())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.bufferlist_item, null);
            holder = new ViewHolder();
            holder.shortname = (TextView) convertView.findViewById(R.id.bufferlist_shortname);
            holder.fullname = (TextView) convertView.findViewById(R.id.bufferlist_fullname);
            holder.messagecount = (TextView) convertView
                    .findViewById(R.id.bufferlist_hotlist_messagecount);
            holder.highlightcount = (TextView) convertView
                    .findViewById(R.id.bufferlist_hotlist_highlightcount);

            holder.title = (TextView) convertView.findViewById(R.id.bufferlist_title);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Buffer bufferItem = getItem(position);

        // use contents of bufferItem to fill in text content
        holder.fullname.setText(bufferItem.getFullName());
        holder.shortname.setText(bufferItem.getShortName());
        if (bufferItem.getShortName() == null) {
            holder.shortname.setText(bufferItem.getFullName());
        }

        // Title might be removed in different layouts
        if (holder.title != null && !hideBufferTitles) {
            holder.title.setText(com.ubergeek42.weechat.Color
                    .stripAllColorsAndAttributes(bufferItem.getTitle()));
        }
        // Allow hiding of buffer titles
        if (hideBufferTitles) {
            holder.title.setVisibility(View.GONE);
        } else {
            holder.title.setVisibility(View.VISIBLE);
        }
        
        

        int unreadc = bufferItem.getUnread();
        int highlightc = bufferItem.getHighlights();
        // holder.hotlist.setText(String.format("U:%2d  H:%2d   ", unread, highlight));

        if (highlightc > 0) {
            holder.highlightcount.setText("" + highlightc);
            holder.highlightcount.setTextColor(Color.MAGENTA);
        } else {
            holder.highlightcount.setText("");
        }
        if (unreadc > 0) {
            holder.messagecount.setText("" + (unreadc));
            holder.messagecount.setTextColor(Color.YELLOW);
        } else {
            holder.messagecount.setText("");
            holder.messagecount.setTextColor(Color.WHITE);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView highlightcount;
        TextView messagecount;
        TextView shortname;
        TextView fullname;
        TextView title;
    }

    private final Comparator<Buffer> bufferComparator = new Comparator<Buffer>() {
        @Override
        public int compare(Buffer b1, Buffer b2) {
            int b1Highlights = b1.getHighlights();
            int b2Highlights = b2.getHighlights();
            if (b2Highlights > 0 || b1Highlights > 0) {
                return b2Highlights - b1Highlights;
            }
            return b2.getUnread() - b1.getUnread();
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("chatview_colors")) {
            hideBufferTitles = prefs.getBoolean("hide_buffer_titles", false);
        } else {
            return;
        }
        notifyDataSetChanged();
    }


}
