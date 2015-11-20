package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.ClipboardManager;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;

import java.util.ArrayList;


public class CopyPaste implements EditText.OnLongClickListener, AdapterView.OnItemLongClickListener {

    private AppCompatActivity activity;
    private EditText input;

    public CopyPaste(AppCompatActivity activity, EditText input) {
        this.activity = activity;
        this.input = input;
    }

    // called on long click on input field
    @Override public boolean onLongClick(View v) {
        // do not do anything special if TextView has text
        if (!"".equals(((TextView) v).getText().toString())) return false;

        final ArrayList<String> list = new ArrayList<>();

        // read & trim clipboard
        // noinspection deprecation
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final String clip = (cm.getText() == null) ? "" : cm.getText().toString().trim();

        // copy last messages if they do not equal clipboard
        // if there are no messages, do nothing
        for (String m : P.sentMessages) if (!m.equals(clip)) list.add(m);
        if (list.size() == 0) return false;

        // clean and add clipboard
        if (!"".equals(clip)) list.add(clip);

        final LayoutInflater inflater = LayoutInflater.from(activity);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Paste").setAdapter(
                new BaseAdapter() {
                    @Override public int getCount() {return list.size();}
                    @Override public String getItem(int position) {return list.get(position);}
                    @Override public long getItemId(int position) {return position;}
                    @Override public View getView(int position, View convertView, ViewGroup parent) {
                        if (convertView == null) convertView = inflater.inflate(R.layout.select_dialog_item_material_2_lines, parent, false);
                        TextView v = (TextView) convertView;
                        boolean isClip = (!"".equals(clip) && position == list.size() - 1);
                        v.setText(Utils.unCrLf(getItem(position)));
                        v.setBackgroundResource(isClip ? R.color.special : 0);
                        v.setCompoundDrawablesWithIntrinsicBounds(0, 0, isClip ? R.drawable.ic_paste : 0, 0);
                        return v;
                    }
                }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        input.setText(list.get(which));
                        input.setSelection(input.getText().length());
                    }
                });

        // create dialogue, remove bottom padding and scroll to the end
        AlertDialog d = builder.create();
        final ListView l = d.getListView();
        l.setPadding(l.getPaddingLeft(), l.getPaddingTop(), l.getPaddingRight(), 0);
        l.setStackFromBottom(true);
        d.show();
        return true;
    }

    // called on long click on a chat line
    @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        TextView uiTextView = (TextView) view.findViewById(R.id.chatline_message);
        if (uiTextView == null) return false;

        Line line = (Line) uiTextView.getTag();
        final ArrayList<String> list = new ArrayList<>();

        list.add(line.getNotificationString());
        for (URLSpan url: uiTextView.getUrls())
            list.add(url.getURL());

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Copy").setAdapter(
                new ArrayAdapter<>(activity, R.layout.select_dialog_item_material_2_lines, android.R.id.text1, list),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // noinspection deprecation
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setText(list.get(which));
                    }
                });
        builder.create().show();

        line.clickDisabled = true;
        return true;
    }
}
