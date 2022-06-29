package com.ubergeek42.WeechatAndroid.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.WeechatAndroid.service.SSLHandlerKt;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.HexUtilsKt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Set;

public class CertificateDialog extends DialogFragment {
    final private static @Root Kitty kitty = Kitty.make();

    final @NonNull CharSequence title;
    final @NonNull CharSequence text;
    final @NonNull X509Certificate[] reversedCertificateChain;
    final @Nullable Integer positiveButtonText;
          @Nullable DialogInterface.OnClickListener positiveButtonListener;
    final @Nullable Integer negativeButtonText;
    final @Nullable DialogInterface.OnClickListener negativeButtonListener;
    final boolean allowCertificateSelection;

    final RadioButton[] radioButtons;
    int selectedCertificate = -1;

    // 0-argument constructor required to restore this after app death.
    // it's a tad complicated to save and restore this, so we simply don't show the dialog
    // it's also nearly impossible to simply not save it
    // instead we create a bogus instance and dismiss it in onCreate
    public CertificateDialog() {
        this("", "", new X509Certificate[]{}, null, null, null, null);
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TextUtils.isEmpty(title)) dismiss();
    }

    public CertificateDialog(@NonNull CharSequence title, @NonNull CharSequence text,
                             @NonNull X509Certificate[] certificateChain,
                             @Nullable Integer positiveButtonText,
                             @Nullable DialogInterface.OnClickListener positiveButtonListener,
                             @Nullable Integer negativeButtonText,
                             @Nullable DialogInterface.OnClickListener negativeButtonListener) {
        this.title = title;
        this.text = text;
        this.reversedCertificateChain = Utils.reversed(certificateChain);

        this.positiveButtonText = positiveButtonText;
        this.positiveButtonListener = positiveButtonListener;
        this.negativeButtonText = negativeButtonText;
        this.negativeButtonListener = negativeButtonListener;
        this.allowCertificateSelection = positiveButtonText != null;

        this.setRetainInstance(true);
        this.radioButtons = new RadioButton[reversedCertificateChain.length];
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.certificate_dialog, null);

        TextView textView = viewGroup.findViewById(R.id.text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(text);

        ViewPager viewPager = viewGroup.findViewById(R.id.pager);
        viewPager.setPageMargin((int) getResources().getDimension(R.dimen.dialog_item_padding_vertical));
        viewPager.setAdapter(new CertificatePagerAdapter());
        viewPager.setOffscreenPageLimit(100);
        viewPager.setCurrentItem(reversedCertificateChain.length - 1);

        AlertDialog.Builder builder = new FancyAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(viewGroup);

        if (positiveButtonText != null)
            builder.setPositiveButton(getString(positiveButtonText), positiveButtonListener);

        if (negativeButtonText != null)
            builder.setNegativeButton(getString(negativeButtonText), negativeButtonListener);

        return builder.create();
    }

    @Override public void onStart() {
        super.onStart();
        if (allowCertificateSelection && selectedCertificate == -1) setPositiveButtonEnabled(false);
    }

    void setPositiveButtonEnabled(boolean enabled) {
        AlertDialog dialog = ((AlertDialog) getDialog());
        if (dialog != null) dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
    }

    class CertificatePagerAdapter extends PagerAdapter {
        @NonNull @Override public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = LayoutInflater.from(requireContext()).inflate(
                    R.layout.certificate_dialog_certificate, container, false);

            TextView textView = view.findViewById(R.id.certificate);
            int padding = (int) getResources().getDimension(R.dimen.dialog_item_certificate_text_padding);
            textView.setPadding(padding, padding, padding, padding);
            textView.setText(buildCertificateDescription(
                    requireContext(), reversedCertificateChain[position]));

            RadioButton radioButton = view.findViewById(R.id.radio);
            if (!allowCertificateSelection) radioButton.setVisibility(View.INVISIBLE);
            radioButton.setOnClickListener(v -> {
                selectedCertificate = position;
                for (int i = 0; i < radioButtons.length; i++) {
                    if (i != selectedCertificate && radioButtons[i] != null)
                        radioButtons[i].setChecked(false);
                }
                setPositiveButtonEnabled(true);
            });
            if (selectedCertificate == position) radioButton.performClick();    // restore after rotation
            radioButtons[position] = radioButton;

            container.addView(view);
            return view;
        }

        @Override public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override public int getCount() {
            return reversedCertificateChain.length;
        }

        @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static CharSequence buildCertificateDescription(Context context, X509Certificate certificate) {
        String fingerprint;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            fingerprint = HexUtilsKt.toHexStringLowercase(hash);
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            fingerprint = context.getString(R.string.dialog__certificate__unknown_fingerprint);
        }

        String html = context.getString(R.string.dialog__certificate__certificate_description,
                certificate.getSubjectDN().getName(),
                certificate.getIssuerDN().getName(),
                DateFormat.getDateTimeInstance().format(certificate.getNotBefore()),
                DateFormat.getDateTimeInstance().format(certificate.getNotAfter()),
                fingerprint
        );

        return Html.fromHtml(html);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static CertificateDialog buildUntrustedOrNotPinnedCertificateDialog(
            Context context, X509Certificate[] certificateChain,
            boolean certificateNotPinnedDialog) {
        int title, text, positive;
        if (certificateNotPinnedDialog) {
            title = R.string.dialog__certificate__not_pinned__title;
            text = R.string.dialog__certificate__not_pinned__text;
            positive = R.string.dialog__certificate__not_pinned__pin_selected;
        } else {
            title = R.string.dialog__certificate__untrusted__title;
            text = R.string.dialog__certificate__untrusted__text;
            positive = R.string.dialog__certificate__untrusted__button_accept;
        }

        CertificateDialog dialog = new CertificateDialog(
                context.getString(title),
                context.getString(text),
                certificateChain,
                positive, null,
                R.string.dialog__certificate__button_reject, null);
        dialog.positiveButtonListener = (d, which) -> {
            SSLHandler.getInstance(context).trustCertificate(
                    dialog.reversedCertificateChain[dialog.selectedCertificate]);
            ((WeechatActivity) dialog.requireActivity()).connect();
        };
        return dialog;
    }

    public static CertificateDialog buildBackToSafetyCertificateDialog(
            CharSequence title, CharSequence text, X509Certificate[] certificateChain) {
        return new CertificateDialog(title, text, certificateChain,
                                     null, null,
                                     R.string.dialog__certificate__button_back_to_safety, null);
    }

    public static CertificateDialog buildExpiredCertificateDialog(
            Context context, X509Certificate[] certificateChain) {
        return buildBackToSafetyCertificateDialog(
                context.getString(R.string.dialog__certificate__expired__title),
                context.getString(R.string.dialog__certificate__expired__text),
                certificateChain);
    }

    public static CertificateDialog buildNotYetValidCertificateDialog(
            Context context, X509Certificate[] certificateChain) {
        return buildBackToSafetyCertificateDialog(
                context.getString(R.string.dialog__certificate__not_yet_valid__title),
                context.getString(R.string.dialog__certificate__not_yet_valid__text),
                certificateChain);
    }

    public static CertificateDialog buildInvalidHostnameCertificateDialog(
            Context context, X509Certificate[] certificateChain) {
        CharSequence text;
        try {
            Set<String> certificateHosts = SSLHandlerKt.getCertificateHosts(certificateChain[0]);
            StringBuilder sb = new StringBuilder();
            for (String certificateHost : certificateHosts)
                sb.append(context.getString(
                        R.string.dialog__certificate__invalid_hostname__text__host_line,
                        Html.escapeHtml(certificateHost)));
            String allowedHosts = sb.length() > 0 ?
                    sb.toString() :
                    context.getString(R.string.dialog__certificate__invalid_hostname__text__host_line_empty);
            String html = context.getString(
                    R.string.dialog__certificate__invalid_hostname__text,
                    Html.escapeHtml(P.host), allowedHosts);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                html += context.getString(R.string.dialog__certificate__invalid_hostname__text__android_p_warning);
            text = Html.fromHtml(html);
        } catch (Exception e) {
            text = context.getString(R.string.error__etc__prefix, e.getMessage());
        }

        return buildBackToSafetyCertificateDialog(
                context.getString(R.string.dialog__certificate__invalid_hostname__title),
                text,
                certificateChain);
    }
}
