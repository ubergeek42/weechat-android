package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.text.Html;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;

public class SSLErrorDialogBuilder {
    public static ScrollableDialog buildExpiredCertificateDialog(Context context, X509Certificate certificate) {
        return new ScrollableDialog.BackToSafetyDialog(
                context.getString(R.string.dialog_title_cetificate_expired),
                buildCertificateDescription(context, certificate));
    }

    public static ScrollableDialog buildNotYetValidCertificateDialog(Context context, X509Certificate certificate) {
        return new ScrollableDialog.BackToSafetyDialog(
                context.getString(R.string.dialog_title_cetificate_not_yet_valid),
                buildCertificateDescription(context, certificate));
    }

    // remove the host itself, in case the host is an IP defined in the certificate 'Common Name' as
    // Android does not accept that
    public static ScrollableDialog buildInvalidHostnameDialog(Context context, X509Certificate certificate) {
        String clientHost = P.host;

        StringBuilder sb = new StringBuilder();
        for (String host : SSLHandler.getCertificateHosts(certificate))
            if (!host.equals(clientHost))
                sb.append("<br>\u00A0\u00A0\u00A0\u00A0\u2022\u00A0\u00A0<strong>")
                        .append(Html.escapeHtml(host))
                        .append("</strong>");
        String allowedHosts = sb.toString();

        if (allowedHosts.isEmpty()) allowedHosts = context.getString(R.string.invalid_hostname_dialog_empty);

        String html = context.getString(R.string.invalid_hostname_dialog_body,
                Html.escapeHtml(clientHost), allowedHosts);

        return new ScrollableDialog.BackToSafetyDialog(
                context.getString(R.string.invalid_hostname_dialog_title),
                Html.fromHtml(html));
    }

    public static ScrollableDialog buildUntrustedCertificateDialog(WeechatActivity activity, X509Certificate certificate) {
        return new ScrollableDialog(
                activity.getString(R.string.ssl_cert_dialog_title),
                buildCertificateDescription(activity, certificate),
                R.string.ssl_cert_dialog_accept_button,
                (dialog, which) -> {
                    SSLHandler.getInstance(activity).trustCertificate(certificate);
                    activity.connect();
                },
                R.string.ssl_cert_dialog_reject_button,
                null);
    }

    public static CharSequence buildCertificateDescription(Context context, X509Certificate certificate) {
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
