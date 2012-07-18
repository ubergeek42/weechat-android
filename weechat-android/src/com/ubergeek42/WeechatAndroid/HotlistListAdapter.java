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
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManagerObserver;

public class HotlistListAdapter extends BaseAdapter implements SpinnerAdapter, HotlistManagerObserver, OnNavigationListener {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private static final String TAG = "HotlistListAdapter";
	private HotlistManager hotlistManager;
	protected ArrayList<HotlistItem> hotlist = new ArrayList<HotlistItem>();
	
	
	public HotlistListAdapter(WeechatActivity parentActivity, RelayServiceBinder rsb) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);
		
		hotlistManager = rsb.getHotlistManager();
		hotlistManager.onChanged(this);
	}
	@Override
	public int getCount() {
		return hotlist.size();
	}

	@Override
	public HotlistItem getItem(int position) {
		return hotlist.get(position);
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

        HotlistItem hotlistItem = (HotlistItem) getItem(position);

        // use contents of hotlistItem to fill in text content
        holder.fullname.setText(hotlistItem.getFullName());
        holder.shortname.setText(hotlistItem.buffer_name);

        //holder.title.setText(com.ubergeek42.weechat.Color.stripAllColorsAndAttributes(hotlistItem.getTitle()));

        int unread = hotlistItem.getUnread();
        int highlight = hotlistItem.getHighlights();
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
	public boolean onNavigationItemSelected(int position, long itemId) {
		Log.d(TAG, "position:" + position + " itemId" + itemId);
		// If position is zero, don't do anything
		/*if(position == 0) {
			return false;
		}*/
		// Handles the user clicking on a Hotlist
		HotlistItem h = (HotlistItem) this.getItem(position);
		
		// Start new activity for the given buffer
		Intent i = new Intent(this.parentActivity, WeechatChatviewActivity.class);
		i.putExtra("buffer", h.getFullName());
		this.parentActivity.startActivity(i);

		return true;
	}
	@Override
	public void onHotlistChanged() {
		Log.d(TAG, "onBuffersChanged()");
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				hotlist = hotlistManager.getHotlist();
				notifyDataSetChanged();
			}
		});
	}  
}