package android.support.v7.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import com.ubergeek42.WeechatAndroid.utils.Constants;

import java.util.Collections;
import java.util.LinkedList;

public class FontPreference extends DialogPreference {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public FontPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public @NonNull String getFontPath() {
        return getSharedPreferences().getString(getKey(), Constants.PREF_BUFFER_FONT_D);
    }

    public void setFontPath(@NonNull String path) {
        getSharedPreferences().edit().putString(getKey(), path).commit();
        notifyChanged();
    }

    @Override public CharSequence getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Non-monospace fonts will not work well with alignment.");
        sb.append("\n\nSearch Path:");
        for (String p: FontManager.FONT_DIRS)
            sb.append("\n    ").append(p);
        sb.append("\n\nCurrent Value:\n    ");
        sb.append("".equals(getFontPath()) ? "Default" : getFontPath());
        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FontPreferenceFragment extends PreferenceDialogFragmentCompat implements DialogInterface.OnClickListener {

        private LinkedList<FontManager.FontInfo> fonts;
        private LayoutInflater inflater;

        public static FontPreferenceFragment newInstance(String key) {
            FontPreferenceFragment fragment = new FontPreferenceFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            fonts = FontManager.enumerateFonts();
            Collections.sort(fonts);

            // add a "fake" default monospace font
            fonts.addFirst(new FontManager.FontInfo("Default", "", Typeface.MONOSPACE));

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
                    view = inflater.inflate(android.support.v7.appcompat.R.layout.select_dialog_singlechoice_material, parent, false);
                    CheckedTextView tv = (CheckedTextView) view.findViewById(android.R.id.text1);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setSingleLine();
                }

                FontManager.FontInfo font = (FontManager.FontInfo) getItem(position);
                CheckedTextView tv = (CheckedTextView) view.findViewById(android.R.id.text1);
                tv.setTypeface(font.typeface);
                tv.setText(font.name);
                return view;
            }
        }
    }
}
