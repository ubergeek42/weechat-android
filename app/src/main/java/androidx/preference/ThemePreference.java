package androidx.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.util.AttributeSet;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.Constants;

import java.util.Collections;
import java.util.LinkedList;

public class ThemePreference extends DialogPreference {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public @NonNull String getThemePath() {
        return getSharedPreferences().getString(getKey(), Constants.PREF_COLOR_SCHEME_D);
    }

    public void setThemePath(String path) {
        getSharedPreferences().edit().putString(getKey(), path).commit();
        notifyChanged();
    }

    @Override public CharSequence getSummary() {
        return getContext().getString(R.string.pref_theme_summary,
                ThemeManager.SEARCH_DIR,
                "".equals(getThemePath()) ? getContext().getString(R.string.pref_theme_not_set) : getThemePath());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ThemePreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {

        private LinkedList<ThemeManager.ThemeInfo> themes;

        public static ThemePreferenceFragment newInstance(String key) {
            ThemePreferenceFragment fragment = new ThemePreferenceFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            themes = ThemeManager.enumerateThemes(getContext());
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
