package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatTextView;
import android.text.Html;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;


public class InvalidHostnameDialog extends DialogFragment {

    String hostname;
    Iterable<String> certificateHosts;

    public static InvalidHostnameDialog newInstance(String hostname, Iterable<String> certificateHosts) {
        InvalidHostnameDialog d = new InvalidHostnameDialog();
        d.hostname = hostname;
        d.certificateHosts = certificateHosts;
        d.setRetainInstance(true);
        return d;
    }

    // this can get called before the activity has started
    @NonNull
    @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        final int padding = (int) getResources().getDimension(R.dimen.dialog_padding_full);

        final ScrollView scrollView = new ScrollView(getContext());
        final TextView textView = new AppCompatTextView(getContext());
        final StringBuilder sb = new StringBuilder();
        for (String host : certificateHosts) {
            sb.append("<br>\u00A0\u00A0\u00A0\u00A0\u2022\u00A0\u00A0<strong>")
                    .append(Html.escapeHtml(host)).append("</strong>");
        }
        String allowed = sb.toString();
        if (allowed.isEmpty()) {
            allowed = getString(R.string.invalid_hostname_dialog_empty);
        }
        textView.setText(Html.fromHtml(getString(R.string.invalid_hostname_dialog_body,
                Html.escapeHtml(hostname), allowed)));
        scrollView.addView(textView);

        return new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.invalid_hostname_dialog_title))
                .setView(scrollView, padding, padding/2, padding, 0)
                .setNegativeButton(getString(R.string.invalid_hostname_dialog_button), null)
                .create();
    }

    @Override public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
