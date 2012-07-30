package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManagerObserver;

public class HotlistListAdapter extends BaseAdapter implements ListAdapter, HotlistManagerObserver {
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
        this.onHotlistChanged();
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

    static class ViewHolder {
        TextView hotlist;
        TextView title;
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
            holder.hotlist = (TextView) convertView.findViewById(R.id.hotlist_hotlist);
            holder.title = (TextView) convertView.findViewById(R.id.hotlist_title);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        HotlistItem hotlistItem = getItem(position);

        int unread = hotlistItem.getUnread();
        int highlight = hotlistItem.getHighlights();
        int count = Math.max(unread, highlight);

        holder.hotlist.setText(String.format("%2d ", count));

        if (highlight > 0) {
            holder.hotlist.setTextColor(Color.MAGENTA);
        } else if (unread > 0) {
            holder.hotlist.setTextColor(Color.YELLOW);
        } else {
            holder.hotlist.setTextColor(Color.WHITE);
        }

        holder.title.setText(hotlistItem.getFullName());

        return convertView;
    }
}