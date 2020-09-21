package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;

public class ScrollableDialog extends DialogFragment {
    final @NonNull CharSequence title;
    final @NonNull CharSequence text;
    final @Nullable Integer positiveButtonText;
    final @Nullable DialogInterface.OnClickListener positiveButtonListener;
    final @Nullable Integer negativeButtonText;
    final @Nullable DialogInterface.OnClickListener negativeButtonListener;

    public ScrollableDialog(@NonNull CharSequence title, @NonNull CharSequence text,
                            @Nullable Integer positiveButtonText,
                            @Nullable DialogInterface.OnClickListener positiveButtonListener,
                            @Nullable Integer negativeButtonText,
                            @Nullable DialogInterface.OnClickListener negativeButtonListener) {
        this.title = title;
        this.text = text;
        this.positiveButtonText = positiveButtonText;
        this.positiveButtonListener = positiveButtonListener;
        this.negativeButtonText = negativeButtonText;
        this.negativeButtonListener = negativeButtonListener;
        this.setRetainInstance(true);
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final int padding = (int) getResources().getDimension(R.dimen.dialog_padding_full);

        final ScrollView scrollView = new ScrollView(requireContext());
        final TextView textView = new AppCompatTextView(requireContext());
        textView.setText(text);
        scrollView.addView(textView);
        scrollView.setPadding(padding, padding/2, padding, 0);

        AlertDialog.Builder builder = new FancyAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(scrollView);

        if (positiveButtonText != null)
            builder.setPositiveButton(getString(positiveButtonText), positiveButtonListener);

        if (negativeButtonText != null)
            builder.setNegativeButton(getString(negativeButtonText), negativeButtonListener);

        return builder.create();
    }

    // this should prevent the dialog from being dismissed on activity restart
    // see https://code.google.com/p/android/issues/detail?id=17423
    @Override public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
