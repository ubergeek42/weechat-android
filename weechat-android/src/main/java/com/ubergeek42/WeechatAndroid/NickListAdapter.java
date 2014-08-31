package com.ubergeek42.WeechatAndroid;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferNicklistEye;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickListAdapter extends BaseAdapter implements BufferNicklistEye,
        DialogInterface.OnDismissListener, DialogInterface.OnShowListener {
    private static Logger logger = LoggerFactory.getLogger("NickListAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG;

    private final @NonNull WeechatActivity activity;
    private final @NonNull LayoutInflater inflater;
    private final @NonNull Buffer buffer;
    private @NonNull Buffer.Nick[] nicks = new Buffer.Nick[0];
    private @NonNull AlertDialog dialog;

    // for earlier versions of android, draw another view, black text on white
    // this fixes a bug in earlier versions where background and text color would be the same
    private final static int resource = (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) ?
            R.layout.simple_list_item_1 : android.R.layout.simple_list_item_1;

    public NickListAdapter(@NonNull WeechatActivity activity, @NonNull Buffer buffer) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.buffer = buffer;
    }

    @Override
    public int getCount() {
        return nicks.length;
    }

    @Override
    public Buffer.Nick getItem(int position) {
        return nicks[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = inflater.inflate(resource, parent, false);
        TextView textview = (TextView) convertView;

        Buffer.Nick nick = getItem(position);
        textview.setText(nick.prefix + nick.name);
        return convertView;
    }

    public void onNicklistChanged() {
        if (DEBUG) logger.debug("onNicklistChanged()");
        final Buffer.Nick[] tmp = buffer.getNicksCopy();
        final String title = String.format("%s (%s users)", buffer.short_name, tmp.length);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nicks = tmp;
                notifyDataSetChanged();
                dialog.setTitle(title);
            }
        });
    }

    @Override
    public void onShow(DialogInterface dialog) {
        if (DEBUG) logger.debug("onShow()");
        this.dialog = (AlertDialog) dialog;
        buffer.setBufferNicklistEye(this);
        onNicklistChanged();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (DEBUG) logger.debug("onDismiss()");
        buffer.setBufferNicklistEye(null);
    }
}