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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter,
        OnSharedPreferenceChangeListener {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private FragmentActivity activity = null;
    private Buffer buffer;
    private LinkedList<BufferLine> lines;
    private LinkedList<SpannableLine> spannables = new LinkedList<SpannableLine>();
    private LayoutInflater inflater;
    private SharedPreferences prefs;

    private boolean enableTimestamp = true;
    private boolean enableColor = true;
    private boolean enableFilters = true;
    private String prefix_align = "right";
    protected float letterWidth;
    private float textSize;
    private final DateFormat timestampFormat;
    private LeadingMarginSpan.Standard lmspan = new LeadingMarginSpan.Standard(0, 20);

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer) {
        logger.error("ChatLinesAdapter({}, {})", activity, buffer);
        this.activity = activity;
        this.buffer = buffer;
        this.inflater = LayoutInflater.from(activity);

        prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Load the preferences
        enableColor = prefs.getBoolean("chatview_colors", true);
        enableTimestamp = prefs.getBoolean("chatview_timestamps", true);
        enableFilters = prefs.getBoolean("chatview_filters", true);
        prefix_align = prefs.getString("prefix_align", "right");
        textSize = Float.parseFloat(prefs.getString("text_size", "10"));
        timestampFormat = new SimpleDateFormat(prefs.getString("timestamp_format", "HH:mm:ss"));
        setLetterWidth();
        //onManyLinesAdded();
    }

    private void setLetterWidth() {
        TextView textview = (TextView) inflater.inflate(R.layout.chatview_line, null).findViewById(R.id.chatline_message);
        textview.setTextSize(textSize);
        letterWidth = (textview.getPaint().measureText("m"));
    }

    @Override
    public int getCount() {
        return spannables.size();
    }

    @Override
    public Object getItem(int position) {
        return spannables.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textview;

        // If we don't have the view, or we were using a filteredView, inflate a new one
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.chatview_line, null);
            textview = (TextView) convertView.findViewById(R.id.chatline_message);
            textview.setMovementMethod(LinkMovementMethod.getInstance());
            convertView.setTag(textview);
        } else {
            textview = (TextView) convertView.getTag();
        }

        textview.setTextSize(textSize);
        SpannableLine spannableLine = (SpannableLine) getItem(position);
        textview.setText(spannableLine.spannable);
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
        } else if (key.equals("text_size")) {
            textSize = Float.parseFloat(prefs.getString("text_size", "10"));
            setLetterWidth();
        }
        onManyLinesAdded();
    }

    
    public void clearLines() {
        buffer.clearLines();
        lines.clear();
        onManyLinesAdded();
    }

    // Run the notifyDataSetChanged method in the activity's main thread
    public void onManyLinesAdded() {
        logger.debug("onManyLinesAdded()");
        lines = buffer.getLines();
        spannables.clear();
        if (enableFilters) {
            for (BufferLine line : lines) if (line.isVisible()) spannables.add(makeSpannableLine(line));
        } else {
            for (BufferLine line : lines) spannables.add(makeSpannableLine(line));
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {notifyDataSetChanged();}
        });
    }

    public void onLineAdded() {
        logger.debug("onLineAdded()");
        if (spannables.size() > Buffer.MAXLINES) spannables.removeFirst();
        BufferLine line = buffer.getLines().getLast();
        if (enableFilters && !line.isVisible()) return;
        spannables.add(makeSpannableLine(line));
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {notifyDataSetChanged();}
        });
    }

    private SpannableLine makeSpannableLine(BufferLine line) {

        String timestamp = timestampFormat.format(line.getTimestamp());
        String prefix = line.getPrefix();
        String message = line.getMessage();

        Color.parse(timestamp, prefix, message, line.isHighlighted(), 7, true);

        Spannable spannable = new SpannableString(Color.clean_message);

        Object javaspan;
        for (Color.Span span : Color.final_span_list) {
            switch (span.type) {
                case Color.Span.FGCOLOR:
                    javaspan = new ForegroundColorSpan(span.color | 0xFF000000);
                    break;
                case Color.Span.BGCOLOR:
                    javaspan = new BackgroundColorSpan(span.color | 0xFF000000);
                    break;
                case Color.Span.ITALIC:
                    javaspan = new StyleSpan(Typeface.ITALIC);
                    break;
                case Color.Span.BOLD:
                    javaspan = new StyleSpan(Typeface.BOLD);
                    break;
                case Color.Span.UNDERLINE:
                    javaspan = new UnderlineSpan();
                    break;
                default:
                    continue;
            }
            spannable.setSpan(javaspan, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        lmspan = new LeadingMarginSpan.Standard(0, (int) (letterWidth * ( (float) Color.margin)));
        spannable.setSpan(lmspan, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        Linkify.addLinks(spannable, Linkify.WEB_URLS);
        for (URLSpan urlspan : spannable.getSpans(0, spannable.length(), URLSpan.class)) {
            spannable.setSpan(new URLSpan2(urlspan.getURL()), spannable.getSpanStart(urlspan), spannable.getSpanEnd(urlspan), 0);
            spannable.removeSpan(urlspan);
        }

        return new SpannableLine(spannable, Color.margin);
    }
}

class SpannableLine {
    Spannable spannable;
    int margin;
    SpannableLine(Spannable spannable, int margin) {
        this.spannable = spannable;
        this.margin = margin;
    }
}

class URLSpan2 extends URLSpan {
    public URLSpan2(String url) {super(url);}

    @Override
    public void updateDrawState(TextPaint ds) {ds.setUnderlineText(true);}
}