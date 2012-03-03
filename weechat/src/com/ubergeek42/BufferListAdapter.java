package com.ubergeek42;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.ChatBufferObserver;
import com.ubergeek42.weechat.ChatBuffers;
import com.ubergeek42.weechat.WeechatBuffer;

public class BufferListAdapter extends BaseAdapter implements ChatBufferObserver {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private ChatBuffers cbs;
	public BufferListAdapter(WeechatActivity parentActivity, ChatBuffers cbs) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);
		this.cbs = cbs;
		
		cbs.onChanged(this);
	}
	@Override
	public int getCount() {
		return cbs.getNumBuffers();
	}

	@Override
	public WeechatBuffer getItem(int position) {
		return cbs.getBuffer(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		WeechatBuffer bufferItem = (WeechatBuffer)getItem(position);
		
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.buffer_item,null);
		}
		
		TextView shortname = (TextView)convertView.findViewById(R.id.shortname);
		TextView fullname = (TextView)convertView.findViewById(R.id.fullname);
		TextView status = (TextView)convertView.findViewById(R.id.status);
		TextView title = (TextView)convertView.findViewById(R.id.title);
		
		// use contents of bufferItem to fill in text content
		fullname.setText(bufferItem.getFullName());
		shortname.setText(bufferItem.getShortName());
		if (bufferItem.getShortName()==null)
			shortname.setText(bufferItem.getFullName());

		title.setText(bufferItem.getTitle());
		status.setText("unknown");
		status.setText(bufferItem.getNumNicks() + " users");
		return convertView;
	}
	@Override
	public void onBuffersChanged() {
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				notifyDataSetChanged();
				if (parentActivity.progressDialog!=null && parentActivity.progressDialog.isShowing()) {
					parentActivity.progressDialog.dismiss();
				}
			}
		});
	}
}
