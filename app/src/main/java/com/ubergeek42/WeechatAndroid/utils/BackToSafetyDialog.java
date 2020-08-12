package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;

public class BackToSafetyDialog extends DialogFragment {
    final CharSequence title;
    final CharSequence text;

    public BackToSafetyDialog(CharSequence title, CharSequence text) {
        this.title = title;
        this.text = text;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final int padding = (int) getResources().getDimension(R.dimen.dialog_padding_full);

        final ScrollView scrollView = new ScrollView(requireContext());
        final TextView textView = new AppCompatTextView(requireContext());
        textView.setText(text);
        scrollView.addView(textView);
        scrollView.setPadding(padding, padding/2, padding, 0);

        return new FancyAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(getString(R.string.back_to_safety_dialog_button), null)
                .create();
    }

    @Override public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
