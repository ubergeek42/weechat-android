package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;
import java.util.Arrays;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferObserver;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.NickItem;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManagerObserver;

public class NickListAdapter extends BaseAdapter implements BufferObserver  {
	WeechatActivity parentActivity;
	LayoutInflater inflater;
	private static final String TAG = "NickListAdapter";
	private BufferManager bufferManager;
	private String[] nickCache;
    private Buffer buffer;
	protected ArrayList<NickItem> nicklist = new ArrayList<NickItem>();
	
	public NickListAdapter(WeechatActivity parentActivity, RelayServiceBinder rsb, String[] nickCache) {
		this.parentActivity = parentActivity;
		this.inflater = LayoutInflater.from(parentActivity);
		this.nickCache = nickCache;
	}

	@Override
	public String getItem(int position) {
		return nickCache[position];
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

    static class ViewHolder {
        TextView nick;
    }
    
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.nicklist_item, null);
            holder = new ViewHolder();
            holder.nick = (TextView) convertView.findViewById(R.id.nicklist_nick);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.nick.setText(nickCache[position]);

        return convertView;
	}
	@Override
	public void onLineAdded() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onBufferClosed() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onNicklistChanged() {
		Log.d(TAG, "onNicklistChanged()");
		parentActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
					nickCache = buffer.getNicks();
					Arrays.sort(nickCache);
					notifyDataSetChanged();
			}
		});		
	}

	@Override
	public int getCount() {
		if(nickCache==null) return 0;
		return nickCache.length;
	}
}