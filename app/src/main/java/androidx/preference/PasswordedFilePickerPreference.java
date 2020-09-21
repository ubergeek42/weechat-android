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

public abstract class PasswordedFilePickerPreference extends FilePreference implements DialogFragmentGetter {
    private EditText editText = null;

    public PasswordedFilePickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes) throws Exception {
        String password = editText.getText().toString();
        return saveData(bytes, password);
    }

    // validate & save data here; save null if empty and any string otherwise
    // can throw any exceptions!
    protected abstract String saveData(@Nullable byte[] bytes, @NonNull String password) throws Exception;

    @Override @NonNull public DialogFragment getDialogFragment() {
        return new PasswordedFilePickerPreferenceFragment();
    }

    public static class PasswordedFilePickerPreferenceFragment extends FilePreferenceFragment {
        @SuppressWarnings("deprecation") @SuppressLint("RestrictedApi")
        @Override protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            EditText editText = (EditText) LayoutInflater.from(getContext())
                    .inflate(R.layout.pref_password_edit_text, null);
            int padding = (int) getResources().getDimension(com.ubergeek42.WeechatAndroid.R.dimen.dialog_padding);
            builder.setView(editText, padding, padding, padding, padding);

            ((PasswordedFilePickerPreference) getPreference()).editText = editText;
        }
    }
}

