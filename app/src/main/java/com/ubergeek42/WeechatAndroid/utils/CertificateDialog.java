package com.ubergeek42.WeechatAndroid.utils;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CertificateDialog extends DialogFragment {

    final @NonNull CharSequence title;
    final @NonNull CharSequence text;
    final @NonNull List<X509Certificate> reversedCertificateChain;
    final @Nullable Integer positiveButtonText;
    final @Nullable DialogInterface.OnClickListener positiveButtonListener;
    final @Nullable Integer negativeButtonText;
    final @Nullable DialogInterface.OnClickListener negativeButtonListener;


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
        this.setRetainInstance(true);
    }

    public static CertificateDialog buildUntrustedCertificateDialog(WeechatActivity activity, List<X509Certificate> certificateChain) {
        return new CertificateDialog(
                activity.getString(R.string.ssl_cert_dialog_title),
                "This certificate isn't trusted by Android",
                certificateChain,
                R.string.ssl_cert_dialog_accept_button,
                (dialog, which) -> {
                    SSLHandler.getInstance(activity).trustCertificate(certificateChain.get(0));
                    activity.connect();
                },
                R.string.ssl_cert_dialog_reject_button,
                null);
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.certificate_dialog, null);

        TextView textView = viewGroup.findViewById(R.id.text);
        textView.setText(text);

        ViewPager viewPager = viewGroup.findViewById(R.id.pager);
        viewPager.setPageMargin((int) getResources().getDimension(R.dimen.dialog_item_padding_vertical));
        viewPager.setAdapter(new CertificatePagerAdapter());
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

    class CertificatePagerAdapter extends PagerAdapter {
        @NonNull @Override public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = LayoutInflater.from(requireContext()).inflate(
                    R.layout.certificate_dialog_certificate, container, false);
            TextView textView = view.findViewById(R.id.certificate);
            int padding = (int) (getResources().getDimension(R.dimen.dialog_item_padding_vertical) * 1.5);
            textView.setPadding(padding, padding, padding, padding);
            textView.setText(SSLErrorDialogBuilder.buildCertificateDescription(requireContext(), reversedCertificateChain.get(position)));
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
}
