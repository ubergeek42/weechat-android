package com.ubergeek42.WeechatAndroid.copypaste;

import android.content.Context;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.utils.FancyAlertDialogBuilder;
import com.ubergeek42.WeechatAndroid.utils.LineView;
import com.ubergeek42.weechat.Color;

import java.util.ArrayList;

public class Copy {
    public static boolean showCopyDialog(LineView lineView) {
        Context context = lineView.getContext();
        Line line = (Line) lineView.getTag();
        ArrayList<CharSequence> list = new ArrayList<>();

        line.clickDisabled = true;

        if (!TextUtils.isEmpty(line.prefix))
            list.add(line.getNotificationString());
        list.add(Color.stripEverything(line.message));

        for (URLSpan urlSpan : lineView.getUrls()) {
            String url = urlSpan.getURL();
            if (!list.contains(url)) list.add(url);
        }

        AlertDialog dialog = new FancyAlertDialogBuilder(context)
                .setTitle(R.string.dialog_copy_title)
                .create();

        RecyclerView recyclerView = (RecyclerView) LayoutInflater.from(context)
                .inflate(R.layout.list_dialog, null);
        recyclerView.setAdapter(new CopyAdapter(context, list, item -> {
            setClipboard(context, item);
            dialog.dismiss();
        }));

        dialog.setView(recyclerView);
        dialog.show();
        return true;
    }

    private static void setClipboard(Context context, CharSequence text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setText(text);
    }
}