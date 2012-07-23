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

import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;

public class BufferListAdapter extends BaseAdapter{
	Activity parentActivity;
	LayoutInflater inflater;
	public ArrayList<Buffer> buffers = new ArrayList<Buffer>();

	public BufferListAdapter(Activity parentActivity) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);

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
            holder.messagecount = (TextView) convertView.findViewById(R.id.bufferlist_hotlist_messagecount);
            holder.highlightcount = (TextView) convertView.findViewById(R.id.bufferlist_hotlist_highlightcount);

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
        if(holder.title!=null) {
			holder.title.setText(com.ubergeek42.weechat.Color.stripAllColorsAndAttributes(bufferItem.getTitle()));
		}

        int unreadc = bufferItem.getUnread();
        int highlightc = bufferItem.getHighlights();
        //holder.hotlist.setText(String.format("U:%2d  H:%2d   ", unread, highlight));

        if (highlightc > 0) {
            holder.highlightcount.setText("" + highlightc);
            holder.highlightcount.setTextColor(Color.MAGENTA);
        }
        else {
        	holder.highlightcount.setText("");
        }
        if (unreadc > 0) {
        	holder.messagecount.setText("" + (unreadc - highlightc));
			holder.messagecount.setTextColor(Color.YELLOW);
		} else {
        	holder.messagecount.setText("");
			holder.messagecount.setTextColor(Color.WHITE);
		}

        return convertView;
    }

    static class ViewHolder {
        TextView highlightcount;
		TextView messagecount;
		TextView shortname;
        TextView fullname;
        TextView title;
    }

}
