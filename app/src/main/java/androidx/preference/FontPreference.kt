package androidx.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.Constants;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;

public class FontPreference extends DialogPreference implements DialogFragmentGetter {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public FontPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressWarnings("ConstantConditions")
    private  @NonNull String getFontPath() {
        String path = getSharedPreferences().getString(getKey(), Constants.PREF_BUFFER_FONT_D);
        if (!"".equals(path)) path = new File(path).getName();
        return path;
    }

    private void setFontPath(@NonNull String path) {
        getSharedPreferences().edit().putString(getKey(), path).apply();
        notifyChanged();
    }

    @Override public CharSequence getSummary() {
        String path = getFontPath();
        return "".equals(path) ?
                getContext().getString(R.string.pref__FontPreference__default) :
                path;
    }

    @NonNull @Override public DialogFragment getDialogFragment() {
        return new FontPreferenceFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FontPreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {

        private LinkedList<FontManager.FontInfo> fonts;
        private LayoutInflater inflater;

        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            inflater = (LayoutInflater) requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            fonts = FontManager.enumerateFonts(requireContext());
            Collections.sort(fonts);

            // add a "fake" default monospace font
            fonts.addFirst(new FontManager.FontInfo(getString(R.string.pref__FontPreference__default), "", Typeface.MONOSPACE));

            // get index of currently selected font
            String currentPath = ((FontPreference) getPreference()).getFontPath();
            int idx = 0, checked_item = 0;
            for (FontManager.FontInfo font : fonts) {
                if (font.path.equals(currentPath)) {checked_item = idx; break;}
                idx++;
            }

            builder.setSingleChoiceItems(new FontAdapter(), checked_item, this);
            builder.setPositiveButton(null, null);
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0)
                ((FontPreference) getPreference()).setFontPath(fonts.get(which).path);
            dialog.dismiss();
        }

        @Override public void onDialogClosed(boolean b) {}

        ////////////////////////////////////////////////////////////////////////////////////////////

        public class FontAdapter extends BaseAdapter {
            @Override public int getCount() {
                return fonts.size();
            }

            @Override public Object getItem(int position) {
                return fonts.get(position);
            }

            @Override public long getItemId(int position) {
                return position;
            }

            @Override public View getView(int position, View view, ViewGroup parent) {
                if (view == null) {
                    view = inflater.inflate(androidx.appcompat.R.layout.select_dialog_singlechoice_material, parent, false);
                    CheckedTextView tv = view.findViewById(android.R.id.text1);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setSingleLine();
                }

                FontManager.FontInfo font = (FontManager.FontInfo) getItem(position);
                CheckedTextView tv = view.findViewById(android.R.id.text1);
                tv.setTypeface(font.typeface);
                tv.setText(font.name);
                return view;
            }
        }
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}
