package com.ubergeek42.WeechatAndroid.dialogs;

import android.content.Context;
import android.graphics.LightingColorFilter;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

public class FancyAlertDialogBuilder extends AlertDialog.Builder {
    public FancyAlertDialogBuilder(@NonNull Context context) {
        super(context, R.style.AlertDialogTheme);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().getBackground().
                    setColorFilter(new LightingColorFilter(0xFF000000, P.colorPrimary));
        }
        return dialog;
    }
}
