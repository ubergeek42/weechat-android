package androidx.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.Utils;

public abstract class PasswordedFilePickerPreference extends FilePreference implements DialogFragmentGetter {
    private String password = "";

    public PasswordedFilePickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes) throws Exception {
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

            editText.addTextChangedListener(new Utils.SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    ((PasswordedFilePickerPreference) getPreference())
                            .password = editText.getText().toString();
                }
            });
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////// stuff below is boilerplate code needed to save the password. ridiculous ////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override protected Parcelable onSaveInstanceState() {
        SavedState savedState = new SavedState(super.onSaveInstanceState());
        savedState.password = password;
        return savedState;
    }

    @Override protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState savedState = (SavedState) state;
            super.onRestoreInstanceState(savedState.getSuperState());
            password = savedState.password;
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    static class SavedState extends BaseSavedState {
        String password = "";

        public SavedState(Parcel source) {
            super(source);
            password = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(password);
        }

        public final static Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}

