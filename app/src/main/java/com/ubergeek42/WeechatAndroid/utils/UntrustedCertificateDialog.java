/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.Html;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;

public class UntrustedCertificateDialog extends DialogFragment {

    X509Certificate certificate;

    public static UntrustedCertificateDialog newInstance(X509Certificate certificate) {
        UntrustedCertificateDialog d = new UntrustedCertificateDialog();
        d.certificate = certificate;
        d.setRetainInstance(true);
        return d;
    }

    public UntrustedCertificateDialog() {}

    // this can get called before the activity has started
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        final int padding = (int) getResources().getDimension(R.dimen.dialog_padding_full);

        final ScrollView scrollView = new ScrollView(getContext());
        final TextView textView = new AppCompatTextView(getContext());
        textView.setText(Html.fromHtml(getCertificateDescription()));
        scrollView.addView(textView);

        return new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.ssl_cert_dialog_title))
                .setView(scrollView, padding, padding/2, padding, 0)
                .setPositiveButton(getString(R.string.ssl_cert_dialog_accept_button), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SSLHandler.getInstance(getContext()).trustCertificate(certificate);
                        ((WeechatActivity) getActivity()).connect();
                    }
                })
                .setNegativeButton(getString(R.string.ssl_cert_dialog_reject_button), null)
                .create();
    }

    // this prevents the dialog from being dismissed on activity restart
    // see https://code.google.com/p/android/issues/detail?id=17423
    @Override public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    public String getCertificateDescription() {
        String fingerprint;
        try {fingerprint = new String(Hex.encodeHex(DigestUtils.sha256(certificate.getEncoded())));}
        catch (CertificateEncodingException e) {fingerprint = getString(R.string.ssl_cert_dialog_unknown_fingerprint); }
        return getString(R.string.ssl_cert_dialog_description,
                certificate.getSubjectDN().getName(),
                certificate.getIssuerDN().getName(),
                DateFormat.getDateTimeInstance().format(certificate.getNotBefore()),
                DateFormat.getDateTimeInstance().format(certificate.getNotAfter()),
                fingerprint
        );
    }
}
