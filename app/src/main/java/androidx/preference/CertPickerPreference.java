package androidx.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.cats.Cat;

public class CertPickerPreference extends FilePreference implements DialogFragmentGetter {
    private EditText editText = null;

    public CertPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override @Cat protected void  saveData(@Nullable byte[] bytes) {
        persistString(SSLHandler.setClientCertificate(bytes, editText.getText().toString()) ?
                "ok" : null);
        notifyChanged();
    }

    @Override @NonNull public DialogFragment getDialogFragment() {
        return new CertPickerPreferenceFragment();
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
