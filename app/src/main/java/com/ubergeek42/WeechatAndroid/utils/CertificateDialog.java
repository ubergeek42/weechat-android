package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
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
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CertificateDialog extends DialogFragment {
    final private static @Root Kitty kitty = Kitty.make();

    final @NonNull CharSequence title;
    final @NonNull CharSequence text;
    final @NonNull List<X509Certificate> reversedCertificateChain;
    final @Nullable Integer positiveButtonText;
          @Nullable DialogInterface.OnClickListener positiveButtonListener;
    final @Nullable Integer negativeButtonText;
    final @Nullable DialogInterface.OnClickListener negativeButtonListener;
    final boolean allowCertificateSelection;

    final RadioButton[] radioButtons;
    int selectedCertificate = -1;

    public CertificateDialog(@NonNull CharSequence title, @NonNull CharSequence text,
                             @NonNull List<X509Certificate> certificateChain,
                             @Nullable Integer positiveButtonText,
                             @Nullable DialogInterface.OnClickListener positiveButtonListener,
                             @Nullable Integer negativeButtonText,
                             @Nullable DialogInterface.OnClickListener negativeButtonListener) {
        this.title = title;
        this.text = text;

        this.reversedCertificateChain = new ArrayList<>(certificateChain);
        Collections.reverse(this.reversedCertificateChain);

        this.positiveButtonText = positiveButtonText;
        this.positiveButtonListener = positiveButtonListener;
        this.negativeButtonText = negativeButtonText;
        this.negativeButtonListener = negativeButtonListener;
        this.allowCertificateSelection = positiveButtonText != null;

        this.setRetainInstance(true);
        this.radioButtons = new RadioButton[reversedCertificateChain.size()];
    }


    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.certificate_dialog, null);

        TextView textView = viewGroup.findViewById(R.id.text);
        textView.setText(text);

        ViewPager viewPager = viewGroup.findViewById(R.id.pager);
        viewPager.setPageMargin((int) getResources().getDimension(R.dimen.dialog_item_padding_vertical));
        viewPager.setAdapter(new CertificatePagerAdapter());
        viewPager.setOffscreenPageLimit(100);
        viewPager.setCurrentItem(reversedCertificateChain.size() - 1);

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
            textView.setText(SSLErrorDialogBuilder.buildCertificateDescription(
                    requireContext(), reversedCertificateChain.get(position)));

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
            return reversedCertificateChain.size();
        }

        @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static CertificateDialog buildUntrustedCertificateDialog(WeechatActivity activity,
            List<X509Certificate> certificateChain) {
        CertificateDialog dialog = new CertificateDialog(
                activity.getString(R.string.ssl_cert_dialog_title),
                activity.getString(R.string.ssl_cert_dialog_text),
                certificateChain,
                R.string.ssl_cert_dialog_accept_button, null,
                R.string.ssl_cert_dialog_reject_button, null);
        dialog.positiveButtonListener = (d, which) -> {
            SSLHandler.getInstance(activity).trustCertificate(
                    certificateChain.get(dialog.selectedCertificate));
            activity.connect();
        };
        return dialog;
    }

    public static CertificateDialog buildBackToSafetyCertificateDialog(Context context,
            int title, int text, List<X509Certificate> certificateChain) {
        return new CertificateDialog(
                context.getString(title),
                context.getString(text),
                certificateChain,
                null, null,
                R.string.back_to_safety_dialog_button, null);
    }

    public static CertificateDialog buildExpiredCertificateDialog(Context context,
            List<X509Certificate> certificateChain) {
        return buildBackToSafetyCertificateDialog(context,
                R.string.dialog_title_cetificate_expired,
                R.string.dialog_text_cetificate_expired,
                certificateChain);
    }

    public static CertificateDialog buildNotYetValidCertificateDialog(Context context,
            List<X509Certificate> certificateChain) {
        return buildBackToSafetyCertificateDialog(context,
                R.string.dialog_title_cetificate_not_yet_valid,
                R.string.dialog_text_cetificate_not_yet_valid,
                certificateChain);
    }
}
