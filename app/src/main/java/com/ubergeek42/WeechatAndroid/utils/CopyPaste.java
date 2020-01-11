package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;


public class CopyPaste implements EditText.OnLongClickListener {

    public static CopyPaste copyPaste = new CopyPaste();

    @Override public boolean onLongClick(View v) {
        if (v instanceof EditText) return onLongClickInputField((EditText) v);
        else return onLongClickChatLine((LineView) v);
    }

    private boolean onLongClickInputField(final EditText input) {
        // do not do anything special if TextView has text
        if (!"".equals(input.getText().toString())) return false;
        Context context = input.getContext();

        // read & trim clipboard
        // noinspection deprecation
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        final String clip = (cm == null || cm.getText() == null) ? "" : cm.getText().toString().trim();

        // if no sent messages, or the only sent message is in the clipboard, do nothing
        if (P.sentMessages.size() == 0 || (P.sentMessages.size() == 1 && clip.equals(P.sentMessages.get(0)))) return false;

        final ArrayList<String> list = new ArrayList<>(P.sentMessages);

        // clean and add clipboard
        if (!"".equals(clip)) list.add(clip);

        final LayoutInflater inflater = LayoutInflater.from(context);

        AlertDialog.Builder builder = new FancyAlertDialogBuilder(context);
        builder.setTitle(context.getString(R.string.dialog_paste_title)).setAdapter(
                new BaseAdapter() {
                    @Override public int getCount() {return list.size();}
                    @Override public String getItem(int position) {return list.get(position);}
                    @Override public long getItemId(int position) {return position;}
                    @Override public View getView(int position, View convertView, ViewGroup parent) {
                        if (convertView == null) convertView = inflater.inflate(R.layout.select_dialog_item_material_2_lines, parent, false);
                        TextView v = (TextView) convertView;
                        boolean isClip = (!"".equals(clip) && position == list.size() - 1);
                        v.setText(Utils.unCrLf(getItem(position)));
                        v.setBackgroundResource(isClip ? R.color.pasteBackground : 0);
                        v.setCompoundDrawablesWithIntrinsicBounds(0, 0, isClip ? R.drawable.ic_paste : 0, 0);
                        v.setCompoundDrawablePadding((int) (P._4dp + P._4dp));
                        return v;
                    }
                }, (dialog, which) -> {
                    input.setText(list.get(which));
                    input.setSelection(input.getText().length());
                });

        // create dialogue, remove bottom padding and scroll to the end
        AlertDialog d = builder.create();
        d.show();
        final ListView l = d.getListView();
        l.setPadding(l.getPaddingLeft(), l.getPaddingTop(), l.getPaddingRight(), 0);
        l.setStackFromBottom(true);
        return true;
    }

    // called on long click on a chat line
    private boolean onLongClickChatLine(LineView lineView) {
        final Context context = lineView.getContext();
        Line line = (Line) lineView.getTag();
        final ArrayList<String> list = new ArrayList<>();

        if (!TextUtils.isEmpty(line.prefix)) list.add(line.getNotificationString());
        list.add(Color.stripEverything(line.message));

        for (URLSpan url: lineView.getUrls()) {
            String u = url.getURL();
            if (!list.get(list.size()-1).equals(u)) list.add(u);
        }

        AlertDialog.Builder builder = new FancyAlertDialogBuilder(context);
        builder.setTitle(context.getString(R.string.dialog_copy_title)).setAdapter(
                new ArrayAdapter<>(context, R.layout.select_dialog_item_material_2_lines, android.R.id.text1, list),
                (dialog, which) -> {
                    // noinspection deprecation
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setText(list.get(which));
                });
        builder.create().show();

        line.clickDisabled = true;
        return true;
    }
}
