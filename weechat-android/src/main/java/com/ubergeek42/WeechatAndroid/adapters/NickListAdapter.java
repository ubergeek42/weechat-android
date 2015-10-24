package com.ubergeek42.WeechatAndroid.adapters;

import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferNicklistEye;
import com.ubergeek42.WeechatAndroid.relay.Nick;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickListAdapter extends BaseAdapter implements BufferNicklistEye,
        DialogInterface.OnDismissListener, DialogInterface.OnShowListener {
    private static Logger logger = LoggerFactory.getLogger("NickListAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG;

    private final @NonNull
    WeechatActivity activity;
    private final @NonNull LayoutInflater inflater;
    private final @NonNull Buffer buffer;
    private @NonNull Nick[] nicks = new Nick[0];
    private AlertDialog dialog;

    private int hPadding;
    private int vPadding;

    public NickListAdapter(@NonNull WeechatActivity activity, @NonNull Buffer buffer) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.buffer = buffer;
        hPadding = (int) activity.getResources().getDimension(R.dimen.dialog_item_padding_horizontal);
        vPadding = (int) activity.getResources().getDimension(R.dimen.dialog_item_padding_vertical);
    }

    @Override
    public int getCount() {
        return nicks.length;
    }

    @Override
    public Nick getItem(int position) {
        return nicks[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.support.v7.appcompat.R.layout.select_dialog_item_material, parent, false);
            convertView.setPadding(hPadding, vPadding, hPadding, vPadding);
        }

        Nick nick = getItem(position);
        ((TextView) convertView).setText(nick.prefix + nick.name);
        return convertView;
    }

    public void onNicklistChanged() {
        if (DEBUG) logger.debug("onNicklistChanged()");
        final Nick[] tmp = buffer.getNicksCopy();
        final String title = String.format("%s (%s users)", buffer.shortName, tmp.length);
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