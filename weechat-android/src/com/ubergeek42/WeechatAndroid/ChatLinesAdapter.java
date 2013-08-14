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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;

import android.view.View.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter,
        OnSharedPreferenceChangeListener, OnClickListener {

    private FragmentActivity activity = null;
    private Buffer buffer;
    private EditText mInputBox;
    private LinkedList<BufferLine> lines;
    private LayoutInflater inflater;
    private SharedPreferences prefs;

    private boolean enableTimestamp = true;
    private boolean enableColor = true;
    private boolean enableFilters = true;
    private String prefix_align = "right";
    private int maxPrefix = 0;
    protected int prefixWidth;
    private float textSize;
    private final DateFormat timestampFormat;

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer, EditText inputBox) {
        this.activity = activity;
        this.buffer = buffer;
        this.mInputBox = inputBox;
        this.inflater = LayoutInflater.from(activity);

        prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);

        lines = buffer.getLines();

        // Load the preferences
        enableColor = prefs.getBoolean("chatview_colors", true);
        enableTimestamp = prefs.getBoolean("chatview_timestamps", true);
        enableFilters = prefs.getBoolean("chatview_filters", true);
        prefix_align = prefs.getString("prefix_align", "right");
        textSize = Float.parseFloat(prefs.getString("text_size", "10"));
        timestampFormat = new SimpleDateFormat(prefs.getString("timestamp_format", "HH:mm:ss"));
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
        ViewHolder holder;

        // If we don't have the view, or we were using a filteredView, inflate a new one
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.chatview_line, null);
            holder = new ViewHolder();
            holder.timestamp = (TextView) convertView.findViewById(R.id.chatline_timestamp);
            holder.prefix = (TextView) convertView.findViewById(R.id.chatline_prefix);
            holder.message = (TextView) convertView.findViewById(R.id.chatline_message);

            convertView.setTag(holder);

        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Change the font sizes
        holder.timestamp.setTextSize(textSize);
        holder.prefix.setTextSize(textSize);
        holder.message.setTextSize(textSize);
        holder.prefix.setOnClickListener(this);

        BufferLine chatLine = (BufferLine) getItem(position);

        // Render the timestamp
        if (enableTimestamp) {
            holder.timestamp.setText(timestampFormat.format(chatLine.getTimestamp()));
            holder.timestamp.setPadding(holder.timestamp.getPaddingLeft(),
                    holder.timestamp.getPaddingTop(), 5, holder.timestamp.getPaddingBottom());
        } else {
            holder.timestamp.setText("");
            holder.timestamp.setPadding(holder.timestamp.getPaddingLeft(),
                    holder.timestamp.getPaddingTop(), 0, holder.timestamp.getPaddingBottom());
        }

        // Recalculate the prefix width based on the size of one character(fixed width font)
        if (prefixWidth == 0) {
            holder.prefix.setMinimumWidth(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maxPrefix; i++) {
                sb.append("m");
            }
            holder.prefix.setText(sb.toString());
            holder.prefix.measure(convertView.getWidth(), convertView.getHeight());
            prefixWidth = holder.prefix.getMeasuredWidth();
        }

        // Render the prefix
        if (chatLine.getHighlight()) {
            String prefixStr = chatLine.getPrefix();
            Spannable highlightText = new SpannableString(prefixStr);
            highlightText.setSpan(new ForegroundColorSpan(Color.YELLOW), 0, prefixStr.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            highlightText.setSpan(new BackgroundColorSpan(Color.MAGENTA), 0, prefixStr.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            holder.prefix.setText(highlightText);
        } else {
            if (enableColor) {
                holder.prefix.setText(Html.fromHtml(chatLine.getPrefixHTML()),
                        TextView.BufferType.SPANNABLE);
            } else {
                holder.prefix.setText(chatLine.getPrefix());
            }
        }
        if (prefix_align.equals("right")) {
            holder.prefix.setGravity(Gravity.RIGHT);
            holder.prefix.setMinimumWidth(prefixWidth);
        } else if (prefix_align.equals("left")) {
            holder.prefix.setGravity(Gravity.LEFT);
            holder.prefix.setMinimumWidth(prefixWidth);
        } else {
            holder.prefix.setGravity(Gravity.LEFT);
            holder.prefix.setMinimumWidth(0);
        }

        // Render the message

        if (enableColor) {
            holder.message.setText(Html.fromHtml(chatLine.getMessageHTML()),
                    TextView.BufferType.SPANNABLE);
        } else {
            holder.message.setText(chatLine.getMessage());
        }

        return convertView;
    }

    static class ViewHolder {
        TextView timestamp;
        TextView prefix;
        TextView message;
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
        } else if (key.equals("text_size")) {
            textSize = Float.parseFloat(prefs.getString("text_size", "10"));
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
    
    public void clearLines() {
        buffer.clearLines();
        lines.clear();
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
                    for (BufferLine line : lines) {
                        if (line.getVisible()) {
                            filtered.add(line);
                        }
                    }
                    lines = filtered;
                }

                if (prefix_align.equals("right") || prefix_align.equals("left")) {
                    int maxlength = 0;
                    // Find max prefix width
                    for (BufferLine line : lines) {
                        int tmp = line.getPrefix().length();
                        if (tmp > maxlength) {
                            maxlength = tmp;
                        }
                    }
                    maxPrefix = maxlength;
                    prefixWidth = 0;
                }

                notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onClick(View v) {
        CharSequence prefix = ((TextView) v).getText();

        // check if we have a nickname surrounded by < >
        if (prefix.charAt(0) != '<' || prefix.charAt(prefix.length() - 1) != '>')
            return;

        // check if nick is an @operator or +voiced user
        int nickStart = 1;
        if (prefix.charAt(nickStart) == '@' || prefix.charAt(nickStart) == '+') {
            nickStart = 2;
        }

        // insert text at current position or at selection
        // via http://stackoverflow.com/questions/3609174/android-insert-text-into-edittext-at-current-position
        int selStart = mInputBox.getSelectionStart();
        int selEnd = mInputBox.getSelectionEnd();
        String insNick;

        if (selStart == 0) {
            insNick = prefix.subSequence(nickStart, prefix.length() - 1).toString() + ": ";
        } else {
            insNick = prefix.subSequence(nickStart, prefix.length() - 1).toString() + " ";
        }

        mInputBox.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                insNick, 0, insNick.length());
    }
}
