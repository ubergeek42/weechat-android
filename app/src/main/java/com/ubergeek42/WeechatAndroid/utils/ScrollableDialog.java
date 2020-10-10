package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
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
import com.ubergeek42.weechat.relay.connection.Identity;
import com.ubergeek42.weechat.relay.connection.Server;

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

    static public DialogFragment buildServerNotKnownDialog(Context context,
                                                           Server server, Identity identity) {
        String keyType = identity.getKeyType() == null ?
                context.getString(R.string.dialog_ssh_server_unknown_unknown_key_type) :
                identity.getKeyType().getDisplayName();
        ScrollableDialog dialog = new ScrollableDialog(
                context.getString(R.string.dialog_ssh_server_unknown_title),
                Html.fromHtml(context.getString(R.string.dialog_ssh_server_unknown_text,
                        server, keyType, identity.getSha256keyFingerprint())),
                R.string.dialog_ssh_server_unknown_button_positive, null,
                R.string.dialog_ssh_server_unknown_button_negative, null
        );
        dialog.positiveButtonListener = (v, i) -> {
            P.sshServerKeyVerifier.addServerHostKey(server, identity);
            ((WeechatActivity) dialog.requireActivity()).connect();
        };
        return dialog;
    }

    static public DialogFragment buildServerNotVerifiedDialog(Context context,
                                                              Server server, Identity identity) {
        String keyType = identity.getKeyType() == null ?
                context.getString(R.string.dialog_ssh_server_unknown_unknown_key_type) :
                identity.getKeyType().getDisplayName();
        return new ScrollableDialog(
                context.getString(R.string.dialog_ssh_server_not_verified_title),
                Html.fromHtml(context.getString(R.string.dialog_ssh_server_not_verified_text,
                        server, keyType, identity.getSha256keyFingerprint())),
                null, null,
                R.string.dialog_button_back_to_safety, null
        );
    }
}
