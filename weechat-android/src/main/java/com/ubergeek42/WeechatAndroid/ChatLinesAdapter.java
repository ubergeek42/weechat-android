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

import java.text.SimpleDateFormat;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferEye;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLinesAdapter extends BaseAdapter implements ListAdapter, BufferEye {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG && false;

    private FragmentActivity activity = null;

    private Buffer buffer;
    private Buffer.Line[] lines = new Buffer.Line[0];

    private LayoutInflater inflater;

    public ChatLinesAdapter(FragmentActivity activity, Buffer buffer) {
        if (DEBUG) logger.error("ChatLinesAdapter({}, {})", activity, buffer);
        this.activity = activity;
        this.buffer = buffer;
        this.inflater = LayoutInflater.from(activity);


        //buffer.setBufferEye(this);
        //onLinesChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////



    @Override
    public int getCount() {
        return lines.length;
    }

    @Override
    public Object getItem(int position) {
        return lines[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // If we don't have the view, or we were using a filteredView, inflate a new one
        TextView textview;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.chatview_line, null);
            textview = (TextView) convertView.findViewById(R.id.chatline_message);
            textview.setMovementMethod(LinkMovementMethod.getInstance());
            convertView.setTag(textview);
        } else {
            textview = (TextView) convertView.getTag();
        }

        textview.setTextSize(Buffer.Line.TEXT_SIZE);
        Buffer.Line line = (Buffer.Line) getItem(position);
        textview.setText(line.spannable);

        return convertView;
    }

    public void clearLines() {}

    @Override
    public void onLinesChanged() {
        if (DEBUG) logger.error("onLinesChanged()");
        final Buffer.Line[] l = buffer.getLinesCopy();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lines = l;
                notifyDataSetChanged();
            }
        });
    }

    @Override public void onLinesListed() {}

    @Override
    public void onPropertiesChanged() {}

    @Override
    public void onBufferClosed() {}
}