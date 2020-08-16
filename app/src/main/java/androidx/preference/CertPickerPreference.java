package androidx.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.cats.Cat;

public class CertPickerPreference extends FilePreference {
    private EditText editText = null;

    public CertPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override @Cat protected void  saveData(@Nullable byte[] bytes) {
        persistString(SSLHandler.setClientCertificate(bytes, editText.getText().toString()) ?
                "ok" : null);
        notifyChanged();
    }

    public CertPickerPreferenceFragment makeFragment(String key, int code) {
        CertPickerPreferenceFragment fragment = new CertPickerPreferenceFragment();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        b.putInt("code", code);
        fragment.setArguments(b);
        return fragment;
    }

    public static class CertPickerPreferenceFragment extends FilePreferenceFragment {
        @SuppressWarnings("deprecation") @SuppressLint("RestrictedApi")
        @Override protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            EditText editText = new EditText(getContext());
            editText.setHint(R.string.edittext_password_hint);
            int padding = (int) getResources().getDimension(com.ubergeek42.WeechatAndroid.R.dimen.dialog_padding);
            builder.setView(editText, padding, padding, padding, padding);

            ((CertPickerPreference) getPreference()).editText = editText;
        }
    }
}
