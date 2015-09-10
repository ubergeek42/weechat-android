package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.ClipboardManager;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;

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
        final ArrayList<String> printList = new ArrayList<>();

        // read & trim clipboard
        // noinspection deprecation
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final String clip = (cm.getText() == null) ? "" : cm.getText().toString().trim();

        // copy last messages if they do not equal clipboard
        // if there are no messages, do nothing
        for (String m : BufferList.sentMessages)
            if (!m.equals(clip)) {
                list.add(m);
                printList.add(Utils.cut(m));
            }
        if (list.size() == 0) return false;

        // clean and add clipboard
        if (!"".equals(clip)) {
            list.add(clip);
            printList.add(Utils.cutFirst(clip));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Paste").setItems(printList.toArray(new CharSequence[printList.size()]),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        input.setText(list.get(which));
                        input.setSelection(input.getText().length());
                    }
                });

        // create dialogue and scroll to end without showing scroll bars
        AlertDialog d = builder.create();
        final ListView l = d.getListView();
        l.setVerticalScrollBarEnabled(false);
        d.show();
        d.getListView().setSelection(list.size() - 1);
        l.post(new Runnable() {@Override public void run() {l.setVerticalScrollBarEnabled(true);}});
        return true;
    }

    // called on long click on a chat line
    @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        TextView uiTextView = (TextView) view.findViewById(R.id.chatline_message);
        if (uiTextView == null) return false;

        Buffer.Line line = (Buffer.Line) uiTextView.getTag();
        final ArrayList<String> list = new ArrayList<>();

        list.add(line.getNotificationString());
        for (URLSpan url: uiTextView.getUrls())
            list.add(url.getURL());

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Copy").setItems(list.toArray(new CharSequence[list.size()]),
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
