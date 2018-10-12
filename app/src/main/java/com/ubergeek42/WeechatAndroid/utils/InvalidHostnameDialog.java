package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.Html;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;

import java.security.cert.X509Certificate;
import java.util.Set;


public class InvalidHostnameDialog extends DialogFragment {

    String hostname;
    Set<String> certificateHosts;

    public static InvalidHostnameDialog newInstance(X509Certificate certificate) {
        InvalidHostnameDialog d = new InvalidHostnameDialog();

        // remove the host itself, in case the host is an IP defined in the
        // certificate 'Common Name' (Android does not accept that)
        d.certificateHosts = SSLHandler.getCertificateHosts(certificate);
        d.certificateHosts.remove(P.host);
        d.hostname = P.host;

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
