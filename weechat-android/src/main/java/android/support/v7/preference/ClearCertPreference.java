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

import java.io.File;

public class ClearCertPreference extends DialogPreference {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public ClearCertPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
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

            builder.setTitle(null).setMessage("Clear certificates?").
                    setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    File keystoreFile = new File(getContext().getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");
                    String msg = (keystoreFile.delete()) ? "Keystore removed" : "Could not remove keystore";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                }
            }).setNegativeButton("Cancel", null);
        }

        @Override public void onDialogClosed(boolean b) {}
    }
}
