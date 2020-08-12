package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.text.Html;

import com.ubergeek42.WeechatAndroid.R;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;

public class BackToSafetyDialogBuilder {
    public static BackToSafetyDialog buildExpiredCertificateDialog(Context context, X509Certificate certificate) {
        return new BackToSafetyDialog(context.getString(R.string.dialog_title_cetificate_expired),
                buildCertificateDescription(context, certificate));
    }

    public static BackToSafetyDialog buildNotYetValidCertificateDialog(Context context, X509Certificate certificate) {
        return new BackToSafetyDialog(context.getString(R.string.dialog_title_cetificate_not_yet_valid),
                buildCertificateDescription(context, certificate));
    }

    private static CharSequence buildCertificateDescription(Context context, X509Certificate certificate) {
        String fingerprint;
        try {
            fingerprint = new String(Hex.encodeHex(DigestUtils.sha256(certificate.getEncoded())));
        } catch (CertificateEncodingException e) {
            fingerprint = context.getString(R.string.ssl_cert_dialog_unknown_fingerprint);
        }

        String html = context.getString(R.string.ssl_cert_dialog_description,
                certificate.getSubjectDN().getName(),
                certificate.getIssuerDN().getName(),
                DateFormat.getDateTimeInstance().format(certificate.getNotBefore()),
                DateFormat.getDateTimeInstance().format(certificate.getNotAfter()),
                fingerprint
        );

        return Html.fromHtml(html);
    }

}
