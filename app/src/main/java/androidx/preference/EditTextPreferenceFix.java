package androidx.preference;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

import android.text.InputType;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;

// this is different to EditTextPreference in the following way:
//   - creates EditText using the attributes and keeps it
//   - automatically sets summary to *** if is a password edit

public class EditTextPreferenceFix extends EditTextPreference {
    private AppCompatEditText editText;
    private boolean isPassword = false;
    private static int PASSWORD_MASK = InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    static {if (Build.VERSION.SDK_INT >= 11) PASSWORD_MASK |= (InputType.TYPE_NUMBER_VARIATION_PASSWORD | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD); }

    public EditTextPreferenceFix(Context context) {
        this(context, null);
    }

    public EditTextPreferenceFix(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.editTextPreferenceStyle);
    }

    public EditTextPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EditTextPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        editText = new AppCompatEditText(context, attrs);
        editText.setId(android.R.id.edit);
        isPassword = (editText.getInputType() & PASSWORD_MASK) != 0;
    }

    public AppCompatEditText getEditText() {
        return editText;
    }

    @Override public void setText(String text) {
        super.setText(text);
        notifyChanged();
    }

    @Override public CharSequence getSummary() {
        String summary = super.getSummary().toString();
        String value = getSharedPreferences().getString(getKey(), "");
        return String.format(summary, (value.length() > 0 && isPassword) ? "••••••" : value);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is different to EditTextPreferenceDialogFragmentCompat in the following way:
    //   - receives EditText from the preference along with the attributes
    //   - sets padding of the EditText to 20dp
    //   - puts cursor on the end

    public static class EditTextPreferenceFixFragment extends PreferenceDialogFragmentCompat {
        private AppCompatEditText mEditText;

        public EditTextPreferenceFixFragment() {}

        public static EditTextPreferenceFixFragment newInstance(String key) {
            EditTextPreferenceFixFragment fragment = new EditTextPreferenceFixFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

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
            if (positiveResult) {
                String value = this.mEditText.getText().toString();
                if (this.getEditTextPreference().callChangeListener(value)) {
                    this.getEditTextPreference().setText(value);
                }
            }
        }
    }
}
