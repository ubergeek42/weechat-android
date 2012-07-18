package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;

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

import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManagerObserver;

public class HotlistListAdapter extends BaseAdapter implements SpinnerAdapter, HotlistManagerObserver, OnNavigationListener {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private static final String TAG = "HotlistListAdapter";
	private HotlistManager hotlistManager;
	protected ArrayList<HotlistItem> hotlist = new ArrayList<HotlistItem>();
    private boolean synthetic = true;
    private int currentPosition = 0;

	
	
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
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.hotlist_item, null);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.hotlist_title);
            holder.hotlist = (TextView) convertView.findViewById(R.id.hotlist_hotlist);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        HotlistItem hotlistItem = (HotlistItem) getItem(position);

        // use contents of hotlistItem to fill in text content
        holder.title.setText(hotlistItem.buffer_name);

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
        TextView hotlist;
        TextView title;
    }
	
	@Override
	public boolean onNavigationItemSelected(int position, long itemId) {
		Log.d(TAG, "position:" + position + " itemId" + itemId);
        //if (synthetic) {
        //    synthetic = false;
        //    return true;
        //}

		// If position is current opsition, don't do anything
		if(position == currentPosition) {
			return false;
		}
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
		Log.d(TAG, "onHotlistChanged()");
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				hotlist = hotlistManager.getHotlist();
				notifyDataSetChanged();
			}
		});
	}
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.hotlist_item, null);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.hotlist_title);
         
            holder.hotlist = (TextView) convertView.findViewById(R.id.hotlist_hotlist);
            

            convertView.setTag(holder);
        } else {
        	return convertView;
            //holder = (ViewHolder) convertView.getTag();
        }

        //HotlistItem hotlistItem = (HotlistItem) getItem(position);

               
        holder.title.setText("Weechat");
        holder.hotlist.setText("");

        //holder.title.setText(com.ubergeek42.weechat.Color.stripAllColorsAndAttributes(hotlistItem.getTitle()));

        //holder.hotlist.setText(String.format("U:%2d  H:%2d   ", unread, highlight));

        
        return convertView;		
	}  
}