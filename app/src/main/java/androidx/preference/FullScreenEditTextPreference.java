package androidx.preference;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;


public class FullScreenEditTextPreference extends EditTextPreference implements DialogFragmentGetter {
    final private static @Root Kitty kitty = Kitty.make();

    final private static int FULL_SCREEN_DIALOG_STYLE = R.style.FullScreenAlertDialogTheme;
    final private static int LAYOUT = R.layout.preferences_full_screen_edit_text;
    final private static int ID_TOOLBAR = R.id.toolbar;
    final private static int ID_EDITTEXT = R.id.text;
    final private static int MENU = R.menu.fullscreen_edit_text;
    final private static int MENU_SAVE = R.id.action_save;
    final private static int MENU_DEFAULT = R.id.action_reset_to_default;
    final private static int DISCARD_CHANGES_PROMPT = R.string.pref__FullScreenEditTextPreference__discard_changes_prompt;
    final private static int DISCARD_CHANGES_CANCEL = R.string.pref__FullScreenEditTextPreference__discard_changes_cancel;
    final private static int DISCARD_CHANGES_DISCARD = R.string.pref__FullScreenEditTextPreference__discard_changes_discard;

    final private String defaultValue;

    @SuppressWarnings("unused")
    public FullScreenEditTextPreference(Context context) {
        this(context, null);
    }

    @SuppressWarnings("WeakerAccess")
    public FullScreenEditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.editTextPreferenceStyle);
    }

    @SuppressWarnings("WeakerAccess")
    public FullScreenEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings("WeakerAccess")
    public FullScreenEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FullScreenEditTextPreference, 0, 0);
        defaultValue = a.getString(R.styleable.FullScreenEditTextPreference_resetToDefaultValue);
        a.recycle();
    }

    @Override public CharSequence getSummary() {
        String summary = super.getSummary().toString();
        String value = getSharedPreferences().getString(getKey(), "");
        return String.format(summary, value);
    }

    @NonNull @Override public DialogFragment getDialogFragment() {
        return new FullScreenEditTextPreferenceFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FullScreenEditTextPreferenceFragment extends PreferenceDialogFragmentCompat {
        private EditText editText;

        @Override public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setStyle(DialogFragment.STYLE_NO_FRAME, FULL_SCREEN_DIALOG_STYLE);
        }

        @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = requireContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            @SuppressLint("InflateParams") View contents = inflater.inflate(LAYOUT, null);

            Toolbar toolbar = contents.findViewById(ID_TOOLBAR);
            toolbar.setTitle(getPreference().getTitle());
            toolbar.inflateMenu(MENU);
            toolbar.setNavigationOnClickListener(v -> tryClosing());
            toolbar.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case MENU_SAVE:
                        saveAndDismiss();
                        return true;
                    case MENU_DEFAULT:
                        resetToDefaultValue();
                        return true;
                }
                return false;
            });

            editText = contents.findViewById(ID_EDITTEXT);
            editText.setHorizontallyScrolling(true);
            editText.setText(getFullScreenEditTextPreference().getText());

            final AlertDialog.Builder builder = new AlertDialog.Builder(context, FULL_SCREEN_DIALOG_STYLE);
            builder.setView(contents);
            builder.setOnKeyListener((d, k, e) -> {
                if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                    tryClosing();
                    return true;
                }
                return false;
            });

            Dialog dialog = builder.create();
            if (dialog.getWindow() != null) dialog.getWindow().setDimAmount(0);
            return dialog;
        }

        private void resetToDefaultValue() {
            editText.setText(getFullScreenEditTextPreference().defaultValue);
        }

        private void saveAndDismiss() {
            String value = editText.getText().toString();
            if (this.getFullScreenEditTextPreference().callChangeListener(value)) {
                this.getFullScreenEditTextPreference().setText(value);
                dismiss();
            }
        }

        void tryClosing() {
            String current = editText.getText().toString();
            String stored = getFullScreenEditTextPreference().getText();
            boolean same = TextUtils.isEmpty(current) && TextUtils.isEmpty(stored) ||
                    current.equals(stored);
            if (same) {
                dismiss();
            } else {
                new AlertDialog.Builder(requireContext())
                        .setMessage(DISCARD_CHANGES_PROMPT)
                        .setPositiveButton(DISCARD_CHANGES_DISCARD, (d, w) -> dismiss())
                        .setNegativeButton(DISCARD_CHANGES_CANCEL, null).show();
            }
        }

        private FullScreenEditTextPreference getFullScreenEditTextPreference() {
            return (FullScreenEditTextPreference) this.getPreference();
        }

        @Override public void onDialogClosed(boolean positiveResult) {}
    }
}
