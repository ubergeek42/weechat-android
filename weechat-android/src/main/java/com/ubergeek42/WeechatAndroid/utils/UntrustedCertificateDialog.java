/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatTextView;
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
                .setTitle("Untrusted certificate")
                .setView(scrollView, padding, padding/2, padding, 0)
                .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SSLHandler.getInstance(getContext()).trustCertificate(certificate);
                        ((WeechatActivity) getActivity()).connect();
                    }
                })
                .setNegativeButton("Reject", null)
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
        try {fingerprint = new String(Hex.encodeHex(DigestUtils.sha1(certificate.getEncoded())));}
        catch (CertificateEncodingException e) {fingerprint = "Unknown";}
        return "<b>Issued to:</b><br>" +
                certificate.getSubjectDN().getName() + "<br><br>" +
                "<b>Issued by:</b><br>" +
                certificate.getIssuerDN().getName() + "<br><br>" +
                "<b>Validity Period:</b><br>" +
                "Issued On:  " + certificate.getNotBefore() + "<br>" +
                "Expires On: " + certificate.getNotAfter() + "<br><br>" +
                "<b>SHA-1 Fingerprint:</b><br>" +
                fingerprint;
    }
}
