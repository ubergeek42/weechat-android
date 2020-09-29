// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package androidx.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;

public class ClearKnownHostsPreference extends DialogPreference implements DialogFragmentGetter {
    public ClearKnownHostsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        update();
    }

    private void update() {
        P.loadServerKeyVerifier();
        final int records = P.sshServerKeyVerifier.getNumberOfRecords();
        setEnabled(records > 0);
        setSummary(records + " known hosts");
    }

    @Override @NonNull public DialogFragment getDialogFragment() {
        return new ClearKnownHostsPreferenceFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ClearKnownHostsPreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            builder.setTitle(null).setMessage("Clear known hosts?").
                    setPositiveButton(getString(R.string.pref_clear_certs_positive), (dialog, which) -> {
                        P.sshServerKeyVerifier.clear();
                        Toast.makeText(getContext(), "Known hosts cleared", Toast.LENGTH_LONG).show();
                    }).setNegativeButton(getString(R.string.pref_clear_certs_negative), null);
        }

        @Override
        public void onDialogClosed(boolean b) {
            ((ClearKnownHostsPreference) getPreference()).update();
        }
    }
}
