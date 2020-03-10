package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;


public class CopyPaste implements EditText.OnLongClickListener {

    public static CopyPaste copyPaste = new CopyPaste();

    @Override public boolean onLongClick(View v) {
        return onLongClickChatLine((LineView) v);
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
                new ArrayAdapter<>(context, R.layout.select_dialog_item_material_2_lines, R.id.text, list),
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
