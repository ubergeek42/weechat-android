package androidx.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.ubergeek42.WeechatAndroid.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.DialogFragment;

// this is different to EditTextPreference in the following way:
//   - creates EditText using the attributes and keeps it
//   - automatically sets summary to *** if is a password edit

public class EditTextPreferenceFix extends EditTextPreference implements DialogFragmentGetter {
    private AppCompatEditText editText;
    private boolean isPassword = false;
    private final static int PASSWORD_MASK = InputType.TYPE_TEXT_VARIATION_PASSWORD
            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            | InputType.TYPE_NUMBER_VARIATION_PASSWORD
            | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;

    @SuppressWarnings("unused")
    public EditTextPreferenceFix(Context context) {
        this(context, null);
    }

    @SuppressWarnings("WeakerAccess")
    public EditTextPreferenceFix(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.editTextPreferenceStyle);
    }

    @SuppressWarnings("WeakerAccess")
    public EditTextPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings("WeakerAccess")
    public EditTextPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EditTextPreferenceFix, 0, 0);
        boolean singleLine = a.getBoolean(R.styleable.EditTextPreferenceFix_android_singleLine, true);
        a.recycle();

        editText = new AppCompatEditText(context, attrs);
        editText.setId(android.R.id.edit);
        if (!singleLine) editText.setSingleLine(false);
        isPassword = (editText.getInputType() & PASSWORD_MASK) != 0;
    }

    private AppCompatEditText getEditText() {
        return editText;
    }

    @Override public void setText(String text) {
        super.setText(text);
        notifyChanged();
    }

    @Override public CharSequence getSummary() {
        String summary = super.getSummary().toString();
        String value = getSharedPreferences().getString(getKey(), "");
        return String.format(summary, (!TextUtils.isEmpty(value) && isPassword) ? "••••••" : value);
    }

    @NonNull @Override public DialogFragment getDialogFragment() {
        return new EditTextPreferenceFixFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is different to EditTextPreferenceDialogFragmentCompat in the following way:
    //   - receives EditText from the preference along with the attributes
    //   - sets padding of the EditText to 20dp
    //   - puts cursor on the end

    public static class EditTextPreferenceFixFragment extends PreferenceDialogFragmentCompat {
        private AppCompatEditText mEditText;

        // these two warnings are related to builder.setView(); somehow setting padding on the
        // view itself has no effect
        @SuppressWarnings("deprecation") @SuppressLint("RestrictedApi")
        @Override protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            mEditText = getEditTextPreference().getEditText();

            // we can be reusing the EditText, so remove it from parent, if any
            ViewParent parent = mEditText.getParent();
            if (parent != null)
                ((ViewGroup) parent).removeView(this.mEditText);

            // set text and put cursor on the end
            String value = getEditTextPreference().getText();
            if (value != null) {
                mEditText.setText(value);
                mEditText.setSelection(value.length(), value.length());
            }

            // set padding
            int padding = (int) getResources().getDimension(com.ubergeek42.WeechatAndroid.R.dimen.dialog_padding);
            builder.setView(mEditText, padding, padding, padding, padding);
        }

        private EditTextPreferenceFix getEditTextPreference() {
            return (EditTextPreferenceFix) this.getPreference();
        }

        @Override protected boolean needInputMethod() {
            return true;
        }

        @Override public void onDialogClosed(boolean positiveResult) {
            if (positiveResult && mEditText.getText() != null) {
                String value = mEditText.getText().toString();
                if (this.getEditTextPreference().callChangeListener(value)) {
                    this.getEditTextPreference().setText(value);
                }
            }
        }
    }
}
