package androidx.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;

import java.util.Collections;
import java.util.LinkedList;

public class ThemePreference extends DialogPreference implements DialogFragmentGetter {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String defaultValue;

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private @Nullable String getThemePath() {
        return getSharedPreferences().getString(getKey(), defaultValue);
    }

    private void setThemePath(String path) {
        getSharedPreferences().edit().putString(getKey(), path).apply();
        notifyChanged();
    }


    @Override protected Object onGetDefaultValue(TypedArray a, int index) {
        return defaultValue = a.getString(index);
    }

    @Override public CharSequence getSummary() {
        return TextUtils.isEmpty(getThemePath()) ? getContext().getString(R.string.pref_theme_not_set) : getThemePath();
    }

    @NonNull @Override public DialogFragment getDialogFragment() {
        return new ThemePreferenceFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ThemePreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {

        private LinkedList<ThemeManager.ThemeInfo> themes;

        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            themes = ThemeManager.enumerateThemes(requireContext());
            Collections.sort(themes);

            // find index of the current theme, and while we are at it
            // create a CharSequence[] copy of the theme name list
            CharSequence[] list = new CharSequence[themes.size()];
            String currentPath = ((ThemePreference) getPreference()).getThemePath();
            int idx = 0, checked_item = 0;
            for (ThemeManager.ThemeInfo theme : themes) {
                if (theme.path.equals(currentPath)) checked_item = idx;
                list[idx] = theme.name;
                idx++;
            }

            builder.setSingleChoiceItems(list, checked_item, this);
            builder.setPositiveButton(null, null);
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0)
                ((ThemePreference) getPreference()).setThemePath(themes.get(which).path);
            dialog.dismiss();
        }

        @Override public void onDialogClosed(boolean b) {}
    }
}
