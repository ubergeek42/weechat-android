/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package android.support.v7.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;

import java.text.MessageFormat;

public class ClearCertPreference extends DialogPreference {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public ClearCertPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        update();
    }

    private void update() {
        final int count = SSLHandler.getInstance(getContext()).getUserCertificateCount();
        setEnabled(count > 0);
        setSummary(MessageFormat.format(getContext().getString(R.string.pref_clear_certs_summary), count));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ClearCertPreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {

        public static ClearCertPreferenceFragment newInstance(String key) {
            ClearCertPreferenceFragment fragment = new ClearCertPreferenceFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            builder.setTitle(null).setMessage(getString(R.string.pref_clear_certs_confirmation)).
                    setPositiveButton(getString(R.string.pref_clear_certs_positive), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            boolean removed = SSLHandler.getInstance(getContext()).removeKeystore();
                            String msg = getString(removed ?
                                    R.string.pref_clear_certs_success :
                                    R.string.pref_clear_certs_failure);
                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    }).setNegativeButton(getString(R.string.pref_clear_certs_negative), null);
        }

        @Override
        public void onDialogClosed(boolean b) {
            ((ClearCertPreference) getPreference()).update();
        }
    }
}
