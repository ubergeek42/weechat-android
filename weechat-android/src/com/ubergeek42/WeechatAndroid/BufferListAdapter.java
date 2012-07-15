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

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;

public class BufferListAdapter extends BaseAdapter implements BufferManagerObserver {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private BufferManager bufferManager;
	protected ArrayList<Buffer> buffers = new ArrayList<Buffer>();
	
	
	public BufferListAdapter(WeechatActivity parentActivity, RelayServiceBinder rsb) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);
		
		bufferManager = rsb.getBufferManager();
		bufferManager.onChanged(this);
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
            holder.hotlist = (TextView) convertView.findViewById(R.id.bufferlist_hotlist);
            holder.title = (TextView) convertView.findViewById(R.id.bufferlist_title);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Buffer bufferItem = (Buffer) getItem(position);

        // use contents of bufferItem to fill in text content
        holder.fullname.setText(bufferItem.getFullName());
        holder.shortname.setText(bufferItem.getShortName());
        if (bufferItem.getShortName() == null)
            holder.shortname.setText(bufferItem.getFullName());

        holder.title.setText(com.ubergeek42.weechat.Color.stripAllColorsAndAttributes(bufferItem.getTitle()));

        int unread = bufferItem.getUnread();
        int highlight = bufferItem.getHighlights();
        holder.hotlist.setText(String.format("U:%2d  H:%2d   ", unread, highlight));

        if (highlight > 0) {
            holder.hotlist.setTextColor(Color.MAGENTA);
        } else if (unread > 0) {
            holder.hotlist.setTextColor(Color.YELLOW);
        } else {
            holder.hotlist.setTextColor(Color.WHITE);
        }
        return convertView;
    }

    static class ViewHolder {
        TextView shortname;
        TextView fullname;
        TextView hotlist;
        TextView title;
    }
    
	@Override
	public void onBuffersChanged() {
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				buffers = bufferManager.getBuffers();
				// Sort buffers based on unread count
				Collections.sort(buffers, new Comparator<Buffer>() {
			        @Override public int compare(Buffer b1, Buffer b2) {
			        	// TODO implement as comparable in Buffer class
			        	int b1Highlights = b1.getHighlights();
			        	int b2Highlights = b2.getHighlights();
			        	if(b2Highlights > 0 || b1Highlights > 0) {
			        		return b2Highlights - b1Highlights;
			        	}
			            return b2.getUnread() - b1.getUnread();
			        }
			        
			    });
				notifyDataSetChanged();
			}
		});
	}
}
