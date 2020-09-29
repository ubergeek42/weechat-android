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
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.P;

public class ScrollableDialog extends DialogFragment {
    final @NonNull CharSequence title;
    final @NonNull CharSequence text;
    final @Nullable Integer positiveButtonText;
          @Nullable DialogInterface.OnClickListener positiveButtonListener;
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    static public DialogFragment buildServerNotKnownDialog(String host, int port,
            String keyType, byte[] key, String sha256fingerprint) {
        String server = port == 22 ? host : host + ":" + port;
        ScrollableDialog dialog = new ScrollableDialog(
                "Unknown server",
                "Server at " + server + " is not known.\n\n" +
                        keyType + " key SHA256 fingerprint:\n" + sha256fingerprint,
                R.string.dialog_button_accept_selected, null,
                R.string.dialog_button_reject, null
        );
        dialog.positiveButtonListener = (v, i) -> {
            P.sshServerKeyVerifier.addServerHostKey(host, port, keyType, key);
            ((WeechatActivity) dialog.requireActivity()).connect();
        };
        return dialog;
    }

    static public DialogFragment buildServerNotVerifiedDialog(String host, int port,
            String keyType, String sha256fingerprint) {
        String server = port == 22 ? host : host + ":" + port;
        return new ScrollableDialog(
                "Server not verified",
                "Warning: it's possible that someone is doing evil things!\n\n" +
                        "Server " + server + " is known, " +
                        "but its key doesn't match any of the keys that we have.\n\n" +
                        keyType + " key SHA256 fingerprint:\n" + sha256fingerprint + "\n\n" +
                        "If you want to continue, please clear known SSH hosts in preferences.",
                null, null,
                R.string.dialog_button_back_to_safety, null
        );
    }
}
