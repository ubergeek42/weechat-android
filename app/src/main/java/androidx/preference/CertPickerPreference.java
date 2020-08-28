package androidx.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;

public class CertPickerPreference extends FilePreference implements DialogFragmentGetter {
    private EditText editText = null;

    public CertPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes) throws Exception {
        String password = editText.getText().toString();
        SSLHandler.getInstance(getContext()).setClientCertificate(bytes, password);
        persistString(bytes != null ? "ok" : null);
        notifyChanged();
        return bytes == null ? DEFAULT_SUCCESSFULLY_CLEARED : DEFAULT_SUCCESSFULLY_SET;
    }

    @Override @NonNull public DialogFragment getDialogFragment() {
        return new CertPickerPreferenceFragment();
    }

    public static class CertPickerPreferenceFragment extends FilePreferenceFragment {
        @SuppressWarnings("deprecation") @SuppressLint("RestrictedApi")
        @Override protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            EditText editText = (EditText) LayoutInflater.from(getContext())
                    .inflate(R.layout.pref_password_edit_text, null);
            int padding = (int) getResources().getDimension(com.ubergeek42.WeechatAndroid.R.dimen.dialog_padding);
            builder.setView(editText, padding, padding, padding, padding);

            ((CertPickerPreference) getPreference()).editText = editText;
        }
    }
}
