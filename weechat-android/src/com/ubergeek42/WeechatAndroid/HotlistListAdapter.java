package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.ActionBar.OnNavigationListener;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;

public class HotlistListAdapter extends BaseAdapter implements SpinnerAdapter, BufferManagerObserver, OnNavigationListener {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private static final String TAG = "HotlistListAdapter";
	private BufferManager bufferManager;
	protected ArrayList<Buffer> buffers = new ArrayList<Buffer>();
	
	
	public HotlistListAdapter(WeechatActivity parentActivity, RelayServiceBinder rsb) {
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
		// TODO Auto-generated method stub
		Log.d(TAG, "onBuffersChanged()");
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				buffers = bufferManager.getBuffers();
				// Sort buffers based on unread count
				// TODO implement as comparable in Buffer class
				Collections.sort(buffers, new Comparator<Buffer>() {
			        @Override public int compare(Buffer b1, Buffer b2) {
			        	
			        	
			        	String name = b1.getFullName();
			        	//Log.d(TAG, "title:"+name);
			        	if (name == "core.weechat") {
			        		return -1;
			        	}
			        	
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
	@Override
	public boolean onNavigationItemSelected(int position, long itemId) {
		Log.d(TAG, "position:" + position + " itemId" + itemId);
		// If position is zero, don't do anything
		if(position == 0) {
			return false;
		}
		// Handles the user clicking on a buffer
		Buffer b = (Buffer) this.getItem(position);
		
		// Start new activity for the given buffer
		Intent i = new Intent(this.parentActivity, WeechatChatviewActivity.class);
		i.putExtra("buffer", b.getFullName());
		this.parentActivity.startActivity(i);

		return true;
	}  
}