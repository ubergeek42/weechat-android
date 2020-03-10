package com.ubergeek42.WeechatAndroid.copypaste;

import android.content.Context;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.WeechatAndroid.media.StrategyUrl;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.FancyAlertDialogBuilder;
import com.ubergeek42.WeechatAndroid.utils.Linkify;

import java.util.ArrayList;
import java.util.List;

public class Paste {
    static class PasteItem {
        final String text;
        final boolean isPaste;
        final @Nullable StrategyUrl strategyUrl;

        PasteItem(String text, boolean isPaste) {
            this.text = text;
            this.isPaste = isPaste;

            String url = Linkify.getFirstUrlFromString(text);
            strategyUrl = url == null ? null : Engine.getStrategyUrl(url);
        }
    }

    public static boolean showPasteDialog(EditText editText) {
        // do not do anything special if EditText has text
        if (editText.getText().length() != 0)
            return false;

        Context context = editText.getContext();
        String clipboard = getCurrentClipboardAsText(context);
        boolean hasClipboard = !TextUtils.isEmpty(clipboard);

        // if no sent messages, or the only sent message is in the clipboard, do nothing
        if (P.sentMessages.isEmpty() || P.sentMessages.size() == 1 && P.sentMessages.get(0).equals(clipboard))
            return false;

        List<PasteItem> list = new ArrayList<>(P.sentMessages.size());
        for (String message : P.sentMessages)
            list.add(new PasteItem(message, false));

        if (hasClipboard)
            list.add(new PasteItem(clipboard, true));

        AlertDialog dialog = new FancyAlertDialogBuilder(context)
                .setTitle(R.string.dialog_paste_title)
                .create();

        RecyclerView recyclerView = (RecyclerView) LayoutInflater.from(context)
                .inflate(R.layout.list_dialog, null);
        recyclerView.setAdapter(new PasteAdapter(context, list, item -> {
            editText.setText(item.text);
            editText.setSelection(editText.getText().length());
            dialog.dismiss();
        }));

        if (hasClipboard) recyclerView.setPadding(0, 0, 0, 0);
        dialog.setView(recyclerView);
        dialog.show();
        return true;
    }

    private static @Nullable String getCurrentClipboardAsText(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || cm.getText() == null)
            return null;
        String text = cm.getText().toString().trim();
        return TextUtils.isEmpty(text) ? null : text;
    }
}
