package com.ubergeek42.WeechatAndroid;

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
	private RelayServiceBinder rsb;
	public BufferListAdapter(WeechatActivity parentActivity, RelayServiceBinder rsb) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);
		this.rsb = rsb;
		
		bufferManager = rsb.getBufferManager();
		bufferManager.onChanged(this);
	}
	@Override
	public int getCount() {
		return bufferManager.getNumBuffers();
	}

	@Override
	public Buffer getItem(int position) {
		return bufferManager.getBuffer(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		Buffer bufferItem = (Buffer)getItem(position);
		
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.bufferlist_item,null);
		}
		
		TextView shortname = (TextView)convertView.findViewById(R.id.bufferlist_shortname);
		TextView fullname = (TextView)convertView.findViewById(R.id.bufferlist_fullname);
		TextView hotlist = (TextView)convertView.findViewById(R.id.bufferlist_hotlist);
		TextView title = (TextView)convertView.findViewById(R.id.bufferlist_title);

		// use contents of bufferItem to fill in text content
		fullname.setText(bufferItem.getFullName());
		shortname.setText(bufferItem.getShortName());
		if (bufferItem.getShortName()==null)
			shortname.setText(bufferItem.getFullName());

		title.setText(com.ubergeek42.weechat.Color.stripIRCColors(bufferItem.getTitle()));
		
		int unread = bufferItem.getUnread();
		int highlight = bufferItem.getHighlights();
		hotlist.setText(String.format("U:%2d  H:%2d   ", unread, highlight));

		if (highlight>0) {
			hotlist.setTextColor(Color.MAGENTA);
		} else if (unread>0) {
			hotlist.setTextColor(Color.YELLOW);
		} else {
			hotlist.setTextColor(Color.WHITE);
		}
		return convertView;
	}
	@Override
	public void onBuffersChanged() {
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				notifyDataSetChanged();
			}
		});
	}
}
