package com.ubergeek42.WeechatAndroid;

import java.util.LinkedList;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter, OnSharedPreferenceChangeListener {

	private WeechatChatviewActivity activity = null;
	private Buffer buffer;
	private LinkedList<BufferLine> lines;
	private LayoutInflater inflater;
	private SharedPreferences prefs;
	
	private boolean enableTimestamp = true;
	private boolean enableColor = true;
	private boolean enableFilters = true;
	private String prefix_align = "right";
	private int maxPrefix = 0;
	protected int prefixWidth;
	
	public ChatLinesAdapter(WeechatChatviewActivity activity,
			Buffer buffer) {
		this.activity = activity;
		this.buffer = buffer;
		this.inflater = LayoutInflater.from(activity);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
		prefs.registerOnSharedPreferenceChangeListener(this);
		
		lines = buffer.getLines();
		
		// Load the preferences
		enableColor = prefs.getBoolean("chatview_colors", true);
		enableTimestamp = prefs.getBoolean("chatview_timestamps", true);
		enableFilters = prefs.getBoolean("chatview_filters", true);
		prefix_align = prefs.getString("prefix_align", "right");
	}

	@Override
	public int getCount() {
		return lines.size();
	}

	@Override
	public Object getItem(int position) {
		return lines.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		BufferLine chatLine = (BufferLine)getItem(position);
		
		// If we don't have the view, or we were using a filteredView, inflate a new one
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.chatview_line,null);
		}

		// Render the timestamp
		TextView timestamp = (TextView) convertView.findViewById(R.id.chatline_timestamp);
		if (enableTimestamp) {
			timestamp.setText(chatLine.getTimestampStr());
			timestamp.setPadding(timestamp.getPaddingLeft(), timestamp.getPaddingTop(), 5, timestamp.getPaddingBottom());
		} else {
			timestamp.setText("");
			timestamp.setPadding(timestamp.getPaddingLeft(), timestamp.getPaddingTop(), 0, timestamp.getPaddingBottom());
		}
		
		TextView prefix = (TextView) convertView.findViewById(R.id.chatline_prefix);
		
		// Recalculate the prefix width based on the size of one character(fixed width font)
		if (prefixWidth == 0) {
			prefix.setMinimumWidth(0);
			StringBuilder sb = new StringBuilder();
			for(int i=0;i<maxPrefix;i++)
				sb.append("m");
			prefix.setText(sb.toString());
			prefix.measure(convertView.getWidth(), convertView.getHeight());
			prefixWidth = prefix.getMeasuredWidth(); 
		}
		
		// Render the prefix
		if(chatLine.getHighlight()) {
			String prefixStr = chatLine.getPrefix();
			Spannable highlightText = new SpannableString(prefixStr);
			highlightText.setSpan(new ForegroundColorSpan(Color.YELLOW), 0, prefixStr.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
			highlightText.setSpan(new BackgroundColorSpan(Color.MAGENTA), 0, prefixStr.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
			prefix.setText(highlightText);
		} else {
			if (enableColor) {
				prefix.setText(Html.fromHtml(chatLine.getPrefixHTML()), TextView.BufferType.SPANNABLE);
			} else {
				prefix.setText(chatLine.getPrefix());
			}
		}
		if (prefix_align.equals("right")) {
			prefix.setGravity(Gravity.RIGHT);
			prefix.setMinimumWidth(prefixWidth);
		} else if (prefix_align.equals("left")) {
			prefix.setGravity(Gravity.LEFT);
			prefix.setMinimumWidth(prefixWidth);
		} else {
			prefix.setGravity(Gravity.LEFT);
			prefix.setMinimumWidth(0);
		}

		// Render the message
		TextView message = (TextView) convertView.findViewById(R.id.chatline_message);
		if (enableColor) {
			message.setText(Html.fromHtml(chatLine.getMessageHTML()), TextView.BufferType.SPANNABLE);
		} else {
			message.setText(chatLine.getMessage());
		}
		
		return convertView;
	}

	
	// Change preferences immediately
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("chatview_colors")) {
			enableColor = prefs.getBoolean("chatview_colors", true);
		} else if (key.equals("chatview_timestamps")) {
			enableTimestamp = prefs.getBoolean("chatview_timestamps", true);
		} else if (key.equals("chatview_filters")) {
			enableFilters = prefs.getBoolean("chatview_filters", true);
		} else if (key.equals("prefix_align_right")) {
			prefix_align = prefs.getString("prefix_align", "right");
		} else {
			return; // Exit before running the notifyChanged function
		}
		notifyChanged();
	}
	
	// Provide a couple of methods for quick toggling timestamps/filters
	public void toggleTimestamps() {
		enableTimestamp = !enableTimestamp;
		notifyChanged();
	}
	public void toggleFilters() {
		enableFilters = !enableFilters;
		notifyChanged();
	}
	
	// Run the notifyDataSetChanged method in the activity's main thread
	public void notifyChanged() {
		activity.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				lines = buffer.getLines();

				if (enableFilters) {
					LinkedList<BufferLine> filtered = new LinkedList<BufferLine>();
					for(BufferLine line: lines) {
						if (line.getVisible()) filtered.add(line);
					}
					lines = filtered;
				}
				
				if (prefix_align.equals("right") || prefix_align.equals("left")) {
					int maxlength = 0;
					// Find max prefix width
					for(BufferLine line: lines) {
						int tmp = line.getPrefix().length();
						if (tmp > maxlength) maxlength = tmp;
					}
					maxPrefix = maxlength;
					prefixWidth = 0;
				}

				notifyDataSetChanged();
			}
		});
	}
}
