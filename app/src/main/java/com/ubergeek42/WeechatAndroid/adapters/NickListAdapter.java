package com.ubergeek42.WeechatAndroid.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferNicklistEye;
import com.ubergeek42.WeechatAndroid.relay.Nick;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;


public class NickListAdapter extends BaseAdapter implements BufferNicklistEye,
        DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

    final private static @Root Kitty kitty = Kitty.make();

    private final @NonNull Context context;
    private final @NonNull LayoutInflater inflater;
    private final @NonNull Buffer buffer;
    private @NonNull Nick[] nicks = new Nick[0];
    private final int awayNickTextColor;
    private AlertDialog dialog;

    @MainThread public NickListAdapter(@NonNull Context context, @NonNull Buffer buffer) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.buffer = buffer;
        awayNickTextColor = ContextCompat.getColor(context, R.color.away_nick);
    }

    @MainThread @Override public int getCount() {
        return nicks.length;
    }

    @MainThread @Override public Nick getItem(int position) {
        return nicks[position];
    }

    @MainThread @Override public long getItemId(int position) {
        return position;
    }

    @MainThread @Override public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = inflater.inflate(R.layout.select_dialog_item_material_2_lines, parent, false);

        final TextView textView = (TextView) convertView;

        Nick nick = getItem(position);
        textView.setText(nick.asString());
        if (nick.isAway()) textView.setTextColor(awayNickTextColor);
        return convertView;
    }

    @AnyThread @Cat synchronized public void onNicklistChanged() {
        final Nick[] newNicks = buffer.getNicksCopy();
        final String nicklistCount = context.getResources().getQuantityString(
                R.plurals.nick_list_count, newNicks.length, newNicks.length);
        final String title = context.getString(R.string.nick_list_title,
                buffer.shortName, nicklistCount);
        Weechat.runOnMainThread(() -> {
            nicks = newNicks;
            notifyDataSetChanged();
            dialog.setTitle(title);
        });
    }

    @MainThread @Override @Cat public void onShow(DialogInterface dialog) {
        this.dialog = (AlertDialog) dialog;
        buffer.setBufferNicklistEye(this);
        onNicklistChanged();
    }

    @MainThread @Override @Cat public void onDismiss(DialogInterface dialog) {
        buffer.setBufferNicklistEye(null);
    }
}